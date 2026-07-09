package com.lemonacademy.ecommerce.shipping.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;

public class PdfReaderTest {

    @Test
    public void testReadPdf() throws Exception {
        File file = new File("icarry-api.pdf");
        if (!file.exists()) {
            System.out.println("icarry-api.pdf not found in root!");
            return;
        }

        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            
            System.out.println("========== PDF TEXT EXTRACTED (Length: " + text.length() + ") ==========");
            
            String[] lines = text.split("\\r?\\n");
            boolean printing = false;
            int printLinesLeft = 0;
            
            for (int i = 180; i < Math.min(lines.length, 415); i++) {
                System.out.println("[PDF ESTIMATE LINE " + i + "] " + lines[i]);
            }
            
            // Also print any lines containing parcel[ or parcel_
            System.out.println("========== SCANNING FOR PARCEL FIELDS ==========");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.toLowerCase().contains("parcel") && (line.toLowerCase().contains("estimate") || line.toLowerCase().contains("type") || line.toLowerCase().contains("value"))) {
                    System.out.println("[PARCEL REF LINE " + i + "] " + line);
                }
            }
        }
    }
}
