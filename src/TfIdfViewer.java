/**
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
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.lang.Math;
import java.util.HashMap;
import java.util.ListIterator;

//import org.apache.lucene.analysis.Analyzer;
//import org.apache.lucene.analysis.standard.StandardAnalyzer;
//import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
//import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermFreqVector;

/** Prints documents in tf-idf vector format and computes cosine similarities */
public class TfIdfViewer {

    /** Simple command-line based search demo. */
    public static void main(String[] args) throws Exception {
        String usage =
                "Usage:\tjava QueryConvert [-index dir]";
        if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
            System.out.println(usage);
            System.exit(0);
        }

        String index = "index";
        String field = "contents";
        String queries = null;
        String queryString = null;

        for(int i = 0;i < args.length;i++) {
            if ("-index".equals(args[i])) {
                index = args[i+1];
                i++;
            } else if ("-field".equals(args[i])) {
                field = args[i+1];
                i++;
            }
        }

        // create a reader and a searcher for the index
        IndexReader reader = IndexReader.open(FSDirectory.open(new File(index)));
        IndexSearcher searcher = new IndexSearcher(reader);

        // create the reader from where we'll read filenames
        BufferedReader in = null;
        if (queries != null) {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(queries), "UTF-8"));
        } else {
            in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        }

        while (true) {

            // get two filenames
            System.out.println("Enter filename 1 (or hit <RETURN>): ");
            String f1 = in.readLine();
            //String f1 = "novels/pg2482.txt";
            if (f1 == null || f1.length() == -1) break;
            f1 = f1.trim(); if (f1.length() == 0) break;

            System.out.println("Enter filename 2: ");
            String f2 = in.readLine();
            //String f2 = "novels/pg1410.txt";

            // get the docId's of the two filenames in the index
            int id1 = findDocId(searcher,f1);
            if (id1 < 0) { System.out.println("No file "+f1+" found in index!"); break; }
            int id2 = findDocId(searcher,f2);
            if (id1 < 0) { System.out.println("No file "+f1+" found in index!"); break; }

            // convert them to tf-idf format
            TermWeight[] v1 = toTfIdf(reader,id1);
            TermWeight[] v2 = toTfIdf(reader,id2);

            // print them out,
            printTermWeightVector(v1);
            printTermWeightVector(v2);

            // and print their cosine similarity
            System.out.println("The cosine similarity of the two files is: "+cosineSimilarity(v1,v2));
        }
        searcher.close();
        reader.close();
    }

    // Searches in the index associated to searcher for a file with field 'path' == filename
    // If none is found, returns -1
    // If at least one is found, returns the docid of one of the matches
    private static int findDocId(IndexSearcher searcher, String filename) throws Exception {
        Term t = new Term("path",filename);
        Query q = new TermQuery(t);
        TopDocs td = searcher.search(q,2);  // get a list of docs matching the query
        if (td.totalHits < 1) return -1;    // no hits found
        else return td.scoreDocs[0].doc;  // returns first matching docId
    }

    // returns the number of documents where string s appears
    private static int docFreq(IndexReader reader, String s) throws Exception {
        return reader.docFreq(new Term("contents",s));
    }

    // Returns an array of TermWeights representing
    // the document whose identifier in reader is docId in tf-idf format,
    // with base 10 logs.
    // The vector is not normalized (may have length != 1)
    private static TermWeight[] toTfIdf(IndexReader reader, int docId) throws Exception {
        // get Lucene representation of a Term-Frequency vector
        TermFreqVector tfv = reader.getTermFreqVector(docId,"contents");
        // split it into two Arrays: one for terms, one for frequencies;
        // Lucene guarantees that terms are sorted
        String[] terms = tfv.getTerms();
        int[] freqs = tfv.getTermFrequencies();
        TermWeight[] tw = new TermWeight[terms.length];
        // compute the maximum frequence of a term in the document
        double fmax = freqs[0];
        for (int i = 1; i < freqs.length; i++) {
            if (freqs[i] > fmax) fmax = freqs[i];
        }

        // number of docs in the index
        int nDocs = reader.numDocs();
        Double tf;
        Double idf;
        for (int i = 0; i < tw.length; i++) {
            int df = reader.docFreq(new Term("contents", terms[i]));
            tf = freqs[i]/fmax;
            idf = Math.log10((double) (nDocs / df));
            Double tf_idf = tf*idf;
            tw[i] = new TermWeight(terms[i],tf_idf);
        }
        return tw;
    }

    // Normalizes the weights in t so that they form a unit-length vector
    // It is assumed that not all weights are 0
    private static TermWeight[] normalize(TermWeight[] t) {
        Double results = 0.0;
        TermWeight[] rw = new TermWeight[1];
        for (TermWeight aT : t) {
            results += aT.getWeight() * aT.getWeight();
        }
        rw[0] = new TermWeight("normalization", Math.sqrt(results));
        return rw;
    }

    // prints the list of pairs (term,weight) in v
    private static void printTermWeightVector(TermWeight[] v) {
        for (TermWeight aT : v) {
            System.out.println("("+aT.getText()+", "+ aT.getWeight()+")");
        }
    }

    // returns the cosine similarity of (the documents represented by) v1 and v2
    // and, as a side effect, normalizes them
    private static double cosineSimilarity(TermWeight[] v1, TermWeight[] v2) {
        double norm_v1 = normalize(v1)[0].getWeight();
        double norm_v2 = normalize(v2)[0].getWeight();
        double v1v2 = 0.0;
        int iv1 = 0;
        int iv2 = 0;
        for(int i = 0; i < Math.max(v1.length, v2.length); i++) {
            String term1 = v1[iv1].getText();
            String term2 = v2[iv2].getText();
            int compare = term1.compareToIgnoreCase(term2);
            if(compare < 0){
                iv1 ++;
            }
            else if(compare > 0 ){
                iv2 ++;
            }
            else{
                v1v2 += v1[iv1].getWeight()*v2[iv2].getWeight();
                iv1 ++;
                iv2 ++;
            }
        }
        return (v1v2/(norm_v1*norm_v2));
    }


}
