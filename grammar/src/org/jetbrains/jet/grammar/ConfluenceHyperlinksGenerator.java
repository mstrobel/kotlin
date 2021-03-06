/*
 * Copyright 2010-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.grammar;

import com.google.common.base.Supplier;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author abreslav
 */
public class ConfluenceHyperlinksGenerator {

    public static void main(String[] args) throws IOException {
        String grammarExtension = "grm";
        File grammarDir = new File("grammar/src");

        List<String> fileNamesInOrder = Arrays.asList(
                "notation",
                "toplevel",
                "class",
                "class_members",
                "enum",
                "types",
                "control",
                "expressions",
                "when",
                "modifiers",
                "attributes",
                "lexical"
            );

        StringBuilder allRules = new StringBuilder();
        Set<File> used = new HashSet<File>();
        for (String fileName : fileNamesInOrder) {
            File file = new File(grammarDir, fileName + "." + grammarExtension);
            used.add(file);
            FileReader fileReader = new FileReader(file);
            try {
                int c;
                while ((c = fileReader.read()) != -1) {
                    allRules.append((char) c);
                }
                allRules.append("\n");
                allRules.append("\n");
            } finally {
                fileReader.close();
            }
        }

        for (File file : grammarDir.listFiles()) {
            if (file.getName().endsWith(grammarExtension)) {
                if (!used.contains(file)) {
                    throw new IllegalStateException("Unused grammar file : " + file.getAbsolutePath());
                }
            }
        }

        StringBuilder output = new StringBuilder();

        Pattern symbolReference = Pattern.compile("^\\w+$", Pattern.MULTILINE);
        Matcher matcher = symbolReference.matcher(allRules);
        int copiedUntil = 0;
        while (matcher.find()) {
            int start = matcher.start();
            output.append(allRules.subSequence(copiedUntil, start));

            String group = matcher.group();
            output.append("&").append(group);
            copiedUntil = matcher.end();
        }
        output.append(allRules.subSequence(copiedUntil, allRules.length()));
//        System.out.println(output);

        _GrammarLexer grammarLexer = new _GrammarLexer((Reader) null);
        grammarLexer
                .reset(output, 0, output.length(), 0);

        StringBuilder result = new StringBuilder("h1. Contents\n").append("{toc:style=disc|indent=20px}");

        Set<String> declaredSymbols = new HashSet<String>();
        Set<String> usedSymbols = new HashSet<String>();
        Multimap<String, String> usages = Multimaps.newSetMultimap(Maps.<String, Collection<String>>newHashMap(), new Supplier<Set<String>>() {
            @Override
            public Set<String> get() {
                return Sets.newHashSet();
            }
        });
        List<Token> tokens = new ArrayList<Token>();
        Declaration lastDeclaration = null;

        while (true) {
            Token advance = grammarLexer.advance();
            if (advance == null) {
                break;
            }
            if (advance instanceof Declaration) {
                Declaration declaration = (Declaration) advance;
                lastDeclaration = declaration;
                declaredSymbols.add(declaration.getName());
            }
            else if (advance instanceof Identifier) {
                Identifier identifier = (Identifier) advance;
                assert lastDeclaration != null;
                usages.put(identifier.getName(), lastDeclaration.getName());
                usedSymbols.add(identifier.getName());
            }
            tokens.add(advance);
        }

        for (Token token : tokens) {
            if (token instanceof Declaration) {
                Declaration declaration = (Declaration) token;
                result.append("{anchor:").append(declaration.getName()).append("}");
                if (!usedSymbols.contains(declaration.getName())) {
//                    result.append("(!) *Unused!* ");
                    System.out.println("Unused: " + token);
                }
                Collection<String> myUsages = usages.get(declaration.getName());
                if (!myUsages.isEmpty()) {
                    result.append("\\[{color:grey}Used by ");
                    for (Iterator<String> iterator = myUsages.iterator(); iterator.hasNext(); ) {
                        String usage = iterator.next();
                        result.append("[#").append(usage).append("]");
                        if (iterator.hasNext()) {
                            result.append(", ");
                        }
                    }
                    result.append("{color}\\]\n");
                }
                result.append(token);
                continue;
            }
            else if (token instanceof Identifier) {
                Identifier identifier = (Identifier) token;
                if (!declaredSymbols.contains(identifier.getName())) {
                    result.append("(!) *Undeclared!* ");
                    System.out.println("Undeclared: " + token);
                }
            }
            result.append(token);
        }

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(result.toString()), new ClipboardOwner() {
            @Override
            public void lostOwnership(Clipboard clipboard, Transferable contents) {

            }
        });
    }

    private static Set<Integer> recordStarts(StringBuilder allRules, String regex) {
        Pattern symbolDeclaration = Pattern.compile(regex, Pattern.MULTILINE);
        Matcher matcher = symbolDeclaration.matcher(allRules);
        Set<Integer> declarationStarts = new HashSet<Integer>();
        while (matcher.find()) {
            if (matcher.groupCount() > 0) {
                declarationStarts.add(matcher.start(1));
            }
            else {
                declarationStarts.add(matcher.start());
            }
        }
        return declarationStarts;
    }
}
