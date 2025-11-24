package com.iot.project;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConsolidatorWorker {

    // Structure pour garder les stats intermédiaires pour une paire (SrcIP, DstIP)
    static class Stats {
        long count;
        double sumFlow;
        double sumFlowSq;
        double sumFwd;
        double sumFwdSq;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java ConsolidatorWorker <summaryCsvPath> <outputConsolidatedCsvPath>");
            System.exit(1);
        }

        String summaryCsv = args[0];
        String outputCsv  = args[1];

        try {
            consolidate(summaryCsv, outputCsv);
            System.out.println("✔ Fichier consolidé généré : " + outputCsv);
        } catch (Exception e) {
            System.err.println("Erreur pendant la consolidation : " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void consolidate(String summaryCsv, String outputCsv)
            throws IOException, CsvValidationException {

        // Map : clé "SrcIP|DstIP" -> Stats
        Map<String, Stats> statsByPair = new HashMap<>();

        try (CSVReader reader = new CSVReader(new FileReader(summaryCsv))) {

            // Lire l'entête
            String[] header = reader.readNext();
            if (header == null) {
                throw new IOException("CSV de résumé vide : " + summaryCsv);
            }

            int idxDate   = findIndex(header, "Date");
            int idxSrcIp  = findIndex(header, "SrcIP");
            int idxDstIp  = findIndex(header, "DstIP");
            int idxFlow   = findIndex(header, "TotalFlowDuration");
            int idxFwdPkt = findIndex(header, "TotalFwdPkt");

            if (idxSrcIp == -1 || idxDstIp == -1 || idxFlow == -1 || idxFwdPkt == -1) {
                throw new IOException("Colonnes manquantes dans le fichier résumé.");
            }

            String[] line;
            long lineCount = 0;

            while ((line = reader.readNext()) != null) {
                lineCount++;

                String srcIp = line[idxSrcIp];
                String dstIp = line[idxDstIp];
                String flowStr = line[idxFlow];
                String fwdStr  = line[idxFwdPkt];

                double flow = parseDoubleSafe(flowStr);
                double fwd  = parseDoubleSafe(fwdStr);

                String key = srcIp + "|" + dstIp;

                Stats s = statsByPair.get(key);
                if (s == null) {
                    s = new Stats();
                    statsByPair.put(key, s);
                }

                s.count++;
                s.sumFlow   += flow;
                s.sumFlowSq += flow * flow;
                s.sumFwd    += fwd;
                s.sumFwdSq  += fwd * fwd;
            }

            System.out.println("Lignes de résumé lues : " + lineCount);
            System.out.println("Nombre de paires (SrcIP,DstIP) : " + statsByPair.size());
        }

        // Écrire le fichier consolidé
        try (CSVWriter writer = new CSVWriter(new FileWriter(outputCsv))) {
            // entête
            String[] outHeader = {
                    "SrcIP", "DstIP",
                    "Count",
                    "MeanFlowDuration", "StdFlowDuration",
                    "MeanFwdPkt", "StdFwdPkt"
            };
            writer.writeNext(outHeader);

            for (Map.Entry<String, Stats> entry : statsByPair.entrySet()) {
                String key = entry.getKey();
                Stats s = entry.getValue();

                String[] parts = key.split("\\|", 2);
                String srcIp = parts[0];
                String dstIp = parts[1];

                double meanFlow = (s.count > 0) ? (s.sumFlow / s.count) : 0.0;
                double meanFwd  = (s.count > 0) ? (s.sumFwd / s.count) : 0.0;

                // variance = E[x^2] - (E[x])^2
                double varFlow = (s.count > 0)
                        ? (s.sumFlowSq / s.count) - (meanFlow * meanFlow)
                        : 0.0;
                double varFwd = (s.count > 0)
                        ? (s.sumFwdSq / s.count) - (meanFwd * meanFwd)
                        : 0.0;

                if (varFlow < 0) varFlow = 0; // pour éviter -0
                if (varFwd < 0)  varFwd  = 0;

                double stdFlow = Math.sqrt(varFlow);
                double stdFwd  = Math.sqrt(varFwd);

                String[] row = {
                        srcIp,
                        dstIp,
                        String.valueOf(s.count),
                        String.valueOf(meanFlow),
                        String.valueOf(stdFlow),
                        String.valueOf(meanFwd),
                        String.valueOf(stdFwd)
                };

                writer.writeNext(row);
            }
        }
    }

    private static int findIndex(String[] header, String colName) {
        for (int i = 0; i < header.length; i++) {
            if (colName.equals(header[i].trim())) {
                return i;
            }
        }
        return -1;
    }

    private static double parseDoubleSafe(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}
