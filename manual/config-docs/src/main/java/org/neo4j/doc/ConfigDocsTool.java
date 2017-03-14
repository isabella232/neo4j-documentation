/*
 * Licensed to Neo Technology under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Neo Technology licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.neo4j.doc;

import org.neo4j.configuration.ConfigValue;
import org.neo4j.function.Predicates;
import org.neo4j.helpers.Args;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tool to generate config documenatation.
 */
public class ConfigDocsTool {

    private static final String DEFAULT_ID = "settings-reference";
    private static final String DEFAULT_TITLE = "Settings reference";
    private static final String DEFAULT_ID_PREFIX = "config_";

    public static void main(String[] args) throws IOException {
        Args arguments = Args.parse(args);
        printUsage();

        List<String> orphans = arguments.orphans();
        Path outFile = orphans.size() == 1 ? Paths.get(orphans.get(0)) : null;

        String id = arguments.has("id") || warnMissingOption("ID", "--id=my-id", DEFAULT_ID)
                ? DEFAULT_ID : arguments.get("id");
        String title = arguments.has("title") || warnMissingOption("title", "--title=my-title", DEFAULT_TITLE)
                ? DEFAULT_TITLE : arguments.get("title");
        String idPrefix = arguments.has("id-prefix") || warnMissingOption("ID prefix", "--id-prefix=my-id-prefix", DEFAULT_ID_PREFIX)
                ? DEFAULT_ID_PREFIX : arguments.get("id-prefix");

        Predicate<ConfigValue> filter = filters(arguments);

        try {
            String doc = new ConfigDocsGenerator().document(filter, id, title, idPrefix);
            if (null != outFile) {
                Path parentDir = outFile.getParent();
                if (!Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }
                System.out.println("Saving docs in '" + outFile.toFile().getAbsolutePath() + "'.");
                Files.write(outFile, doc.getBytes());
            } else {
                System.out.println(doc);
            }
        } catch (NoSuchElementException nse) {
            nse.printStackTrace();
            throw nse;
        } catch (NoSuchFileException nsf) {
            nsf.printStackTrace();
            throw nsf;
        }
    }

    /**
     * Create a combined filter to apply to "all settings" based on arguments.
     * For each of the filters that can be specified as an argument to this tool, create a {@code Predicate<ConfigValue>}.
     * Combine these predicates and pass along to the {@code ConfigDocsGenerator}.
     * @param arguments Arguments passed to this tool.
     * @return A Predicate used for filtering which settings to document.
     */
    private static Predicate<ConfigValue> filters(Args arguments) {
        Predicate<ConfigValue> filters = Predicates.all(arguments.asMap().entrySet().stream()
                .<Predicate<ConfigValue>>flatMap(e -> {
            switch (e.getKey()) {
                // Include deprecated settings?
                // If true, no filter is added. If false, require {@code ConfigValue#isDeprecated()} to be false.
                case "deprecated":
                    return null == e.getValue() || "true".equalsIgnoreCase(e.getValue())
                            ? Stream.empty()
                            : Stream.of(v -> !v.deprecated());
                // Include only deprecated settings.
                case "deprecated-only":
                    return Stream.of(ConfigValue::deprecated);
                // Include internal settings?
                // If true, no filter is added. If false, require {@code ConfigValue#isInternal()} to be false.
                case "internal":
                    return null == e.getValue() || "true".equalsIgnoreCase(e.getValue())
                            ? Stream.empty()
                            : Stream.of(v -> !v.internal());
                // Include only the setting matching this name.
                case "name":
                    return Stream.of(v -> v.name().equals(e.getValue()));
                // Include settings matching any of these names.
                case "names":
                    return Stream.of(v -> Arrays.asList(e.getValue().split(",")).contains(v.name()));
                // Include settings matching this prefix (that are in this namespace).
                case "prefix":
                    return Stream.of(v -> v.name().startsWith(e.getValue()));
                default:
                    return Stream.empty();
            }
        }).collect(Collectors.toList()));
        return arguments.has("unsupported") ? filters : filters.and(v -> !v.internal());
    }

    private static boolean warnMissingOption(String name, String example, String defaultValue) {
        System.out.printf("    [x] No %s provided (%s), using default: '%s'%n", name, example, defaultValue);
        return true;
    }

    private static void printUsage() {
        System.out.printf("Usage: ConfigDocsTool [--options] <out_file>%n");
        System.out.printf("    No options are mandatory but in most cases user will want to set --id, --id-prefix and --title.%n");
        System.out.printf("    If no <out-file> is given prints to stdout.%n");
        System.out.printf("Options:%n");
        System.out.printf("    %-30s%s [%s]%n", "--id", "ID to use for settings summary", DEFAULT_ID);
        System.out.printf("    %-30s%s [%s]%n", "--id-prefix", "ID to prepend to generated ID for each setting details", DEFAULT_ID_PREFIX);
        System.out.printf("    %-30s%s [%s]%n", "--title", "Title to use for settings summary", DEFAULT_ID_PREFIX);
        System.out.printf("Filter options:%n");
        System.out.printf("    %-30s%s [%s]%n", "--deprecated", "Include deprecated settings", true);
        System.out.printf("    %-30s%s [%s]%n", "--deprecated-only", "Include only deprecated settings", false);
        System.out.printf("    %-30s%s [%s]%n", "--name=<name>", "Single setting by name", "");
        System.out.printf("    %-30s%s [%s]%n", "--names=<name1>,<name2>", "Multiple settings by name", "");
        System.out.printf("    %-30s%s [%s]%n", "--prefix=<prefix>", "All settings whose namespace match <prefix>", "");
        System.out.printf("    %-30s%s [%s]%n", "--unsupported", "Include internal/unsupported settings", false);

    }

}
