package org.apache.lucene.demo; /**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Author: Ricard Gavalda, http://www.lsi.upc.edu/gavalda
 * Date: April 10th, 2012
 * Tested on 3.6.0. WILL NOT WORK with lucene 4.x and higher
 * Based on the org.lucene.demo.SearchFiles class
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

//import org.apache.lucene.analysis.Analyzer;
//import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.Term;
//import org.apache.lucene.queryParser.QueryParser;
//import org.apache.lucene.search.IndexSearcher;
//import org.apache.lucene.search.Query;
//import org.apache.lucene.search.ScoreDoc;
//import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
//import org.apache.lucene.util.Version;

/* Open an index given as argument, and print to standard output
 * all the terms contained in its "contents" field, together with their
 * total count (not document count, but total number of occurrences 
 * in the indexed document collection). Order as provided by the index */
public class CountWords3 {
    private static boolean removeStopStopWords = false;
    private static boolean potterStemming = false;


    private CountWords3() {}

    public static boolean isNumeric(String str) {
        NumberFormat formatter = NumberFormat.getInstance();
        ParsePosition pos = new ParsePosition(0);
        formatter.parse(str, pos);
        return str.length() == pos.getIndex();
    }

    public static boolean containsNumber(String str){
        Pattern p = Pattern.compile("([0-9])");
        Matcher m = p.matcher(str);
        return m.find();
    }

    public static boolean containsUnderscore(String str){
        return str.contains("_");
    }

    public static boolean hasUrl(String str){
        String URL_REGEX = "^((https?|ftp)://|(www|ftp)\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)+([/?].*)?$";
        Pattern p = Pattern.compile(URL_REGEX);
        Matcher m = p.matcher(str);
        return m.find();
    }

    public static boolean isImage(String str){
        return str.contains(".jpg") || str.contains(".png");
    }

    public static boolean hasSpecialChars(String str){
        Pattern p = Pattern.compile("[^a-z ]", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(str);
        return m.find();
    }

    public static String removePunctuation(String str){
        str = str.replaceAll("\\p{Punct}+", "");
        return str;
    }

    public static boolean removeTerm(String str){
        if(isNumeric(str) || containsNumber(str)) return true;
        if(containsUnderscore(str)) return true;
        if(isImage(str)) return true;
        if(hasUrl(str)) return true;
        str = removePunctuation(str);
        if(hasSpecialChars(str)) return true;
        return false;
    }

    private static Map<String, Integer> sortByComparator(Map<String, Integer> unsortMap, final boolean order) {

        List<Map.Entry<String, Integer>> list = new LinkedList<Map.Entry<String, Integer>>(unsortMap.entrySet());

        // Sorting the list based on values
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>()
        {
            public int compare(Map.Entry<String, Integer> o1,
                               Map.Entry<String, Integer> o2)
            {
                if (order)
                {
                    return o1.getValue().compareTo(o2.getValue());
                }
                else
                {
                    return o2.getValue().compareTo(o1.getValue());

                }
            }
        });

        // Maintaining insertion order with the help of LinkedList
        Map<String, Integer> sortedMap = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> entry : list)
        {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            String usage = "Usage:\tjava CountWords3 indexdir";
            System.out.println(usage);
            System.exit(0);
        }

        String index = args[0];
        String field = "contents";
        String queries = null;

        IndexReader reader = IndexReader.open(FSDirectory.open(new File(index)));
        TermEnum te = reader.terms();

        int totalOccs = 0;
        int i = 0;
        boolean notlastt = te.next();
        Map<String, Integer> terms = new HashMap<>();
        while (notlastt) {
            Term t = te.term();
            if (t.field() == field) {   // ignore if not desired field
                if(!removeTerm(t.text())) {
                    //t = t.createTerm(removePunctuation(t.text()));
                    TermDocs td = reader.termDocs(t);
                    int n = 0;
                    boolean notlastd = td.next();
                    while (notlastd) {
                       n += td.freq();
                       notlastd = td.next();
                    }
                    String text = removePunctuation(t.text());
                    Integer value;
                    if(terms.containsKey(text)) {
                        value = n + terms.get(text);
                    } else {
                        value = n;
                    }
                    terms.put(text, value);
                    //System.out.println(t.text() + " " + n);
                    totalOccs += n;
                    ++i;
                }
            }
            notlastt = te.next();
        }
        terms = sortByComparator(terms, false);
        int j = 1;
        for (Map.Entry entry : terms.entrySet()) {
            System.out.print(entry.getKey() + " ");
            System.out.print(entry.getValue() + " ");
            System.out.println(j);
            j += 1;
        }
        System.out.println("Distinct words: "+i+"; Word occurrences: "+totalOccs);
    }
}
