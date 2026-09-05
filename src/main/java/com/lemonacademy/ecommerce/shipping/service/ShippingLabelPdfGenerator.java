package com.lemonacademy.ecommerce.shipping.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.lemonacademy.ecommerce.entity.Address;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.entity.OrderItem;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ShippingLabelPdfGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    public byte[] generateShippingLabel(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null for shipping label generation");
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Standard 4x6 inches label size in points (72 points/inch: 288 x 432 pt)
            Rectangle pageSize = new Rectangle(288f, 432f);
            Document document = new Document(pageSize, 10f, 10f, 10f, 10f);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            PdfContentByte cb = writer.getDirectContent();

            // Fonts
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
            Font courierFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(20, 60, 120));
            Font subHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.DARK_GRAY);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);
            Font regularFont = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.BLACK);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 6.5f, Color.DARK_GRAY);
            Font pinFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);

            // Master outer table
            PdfPTable masterTable = new PdfPTable(1);
            masterTable.setWidthPercentage(100);

            // 1. Top Header: Store Brand & Courier Name
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{55f, 45f});

            PdfPCell brandCell = new PdfPCell();
            brandCell.setBorder(Rectangle.NO_BORDER);
            brandCell.addElement(new Paragraph("LEMON HOUSE", headerFont));
            brandCell.addElement(new Paragraph("Handcrafted Products", smallFont));
            headerTable.addCell(brandCell);

            String courierName = order.getCourierName() != null ? order.getCourierName().toUpperCase() : "STANDARD COURIER";
            PdfPCell courierCell = new PdfPCell();
            courierCell.setBorder(Rectangle.NO_BORDER);
            courierCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph cName = new Paragraph(courierName, courierFont);
            cName.setAlignment(Element.ALIGN_RIGHT);
            courierCell.addElement(cName);
            Paragraph cSurface = new Paragraph("SURFACE / PREPAID", subHeaderFont);
            cSurface.setAlignment(Element.ALIGN_RIGHT);
            courierCell.addElement(cSurface);
            headerTable.addCell(courierCell);

            PdfPCell hCell = new PdfPCell(headerTable);
            hCell.setPadding(4f);
            hCell.setBorder(Rectangle.BOTTOM);
            hCell.setBorderWidth(1.5f);
            masterTable.addCell(hCell);

            // 2. Barcode Section (Code 128 for AWB)
            String awb = order.getAwbNumber() != null && !order.getAwbNumber().trim().isEmpty() 
                    ? order.getAwbNumber().trim() 
                    : (order.getShipmentId() != null ? order.getShipmentId() : "AWB" + order.getId().toString().substring(0, 8));

            Barcode128 barcode = new Barcode128();
            barcode.setCodeType(Barcode128.CODE128);
            barcode.setCode(awb);
            barcode.setFont(null); // We render custom text below
            barcode.setBarHeight(34f);
            barcode.setX(1.1f);

            Image barcodeImage = barcode.createImageWithBarcode(cb, Color.BLACK, Color.BLACK);
            barcodeImage.setAlignment(Element.ALIGN_CENTER);

            PdfPCell barcodeCell = new PdfPCell();
            barcodeCell.setBorder(Rectangle.BOTTOM);
            barcodeCell.setBorderWidth(1f);
            barcodeCell.setPaddingTop(5f);
            barcodeCell.setPaddingBottom(4f);
            barcodeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            barcodeCell.addElement(barcodeImage);

            Paragraph awbText = new Paragraph("AWB: " + awb, boldFont);
            awbText.setAlignment(Element.ALIGN_CENTER);
            barcodeCell.addElement(awbText);

            Paragraph shipRefText = new Paragraph("Shipment ID: " + (order.getShipmentId() != null ? order.getShipmentId() : "N/A")
                    + "  |  Order: " + (order.getOrderNumber() != null ? order.getOrderNumber() : order.getId().toString()), smallFont);
            shipRefText.setAlignment(Element.ALIGN_CENTER);
            barcodeCell.addElement(shipRefText);

            masterTable.addCell(barcodeCell);

            // 3. Routing / Destination Info (SHIP TO)
            Address addr = order.getAddress();
            PdfPCell shipToCell = new PdfPCell();
            shipToCell.setBorder(Rectangle.BOTTOM);
            shipToCell.setBorderWidth(1f);
            shipToCell.setPadding(4f);

            shipToCell.addElement(new Paragraph("SHIP TO (CONSIGNEE):", subHeaderFont));
            if (addr != null) {
                shipToCell.addElement(new Paragraph(addr.getFullName() != null ? addr.getFullName().toUpperCase() : "CUSTOMER", boldFont));
                String street = (addr.getAddressLine1() != null ? addr.getAddressLine1() : "") + 
                                (addr.getAddressLine2() != null ? ", " + addr.getAddressLine2() : "");
                shipToCell.addElement(new Paragraph(street, regularFont));
                String cityState = (addr.getCity() != null ? addr.getCity() : "") + 
                                   (addr.getState() != null ? ", " + addr.getState() : "");
                shipToCell.addElement(new Paragraph(cityState, regularFont));

                Paragraph pinPara = new Paragraph("PIN: " + (addr.getPincode() != null ? addr.getPincode() : "N/A"), pinFont);
                shipToCell.addElement(pinPara);

                if (addr.getPhone() != null && !addr.getPhone().isBlank()) {
                    shipToCell.addElement(new Paragraph("Phone: " + addr.getPhone(), boldFont));
                }
            } else {
                shipToCell.addElement(new Paragraph("No Shipping Address Provided", regularFont));
            }
            masterTable.addCell(shipToCell);

            // 4. Shipment Details & Package Specs (2 columns)
            PdfPTable specsTable = new PdfPTable(2);
            specsTable.setWidthPercentage(100);
            specsTable.setWidths(new float[]{50f, 50f});

            PdfPCell pkgCell = new PdfPCell();
            pkgCell.setBorder(Rectangle.NO_BORDER);
            int weight = order.getWeight() != null ? order.getWeight() : 500;
            pkgCell.addElement(new Paragraph("Weight: " + weight + " gm", boldFont));
            String dims = (order.getLength() != null ? order.getLength() : 10) + "x" +
                          (order.getBreadth() != null ? order.getBreadth() : 10) + "x" +
                          (order.getHeight() != null ? order.getHeight() : 10) + " cm";
            pkgCell.addElement(new Paragraph("Dims: " + dims, regularFont));
            pkgCell.addElement(new Paragraph("Date: " + (order.getCreatedAt() != null ? order.getCreatedAt().format(DATE_FORMATTER) : "N/A"), smallFont));
            specsTable.addCell(pkgCell);

            PdfPCell payCell = new PdfPCell();
            payCell.setBorder(Rectangle.NO_BORDER);
            payCell.addElement(new Paragraph("Payment: PREPAID", boldFont));
            String totalAmt = order.getTotalAmount() != null ? "\u20B9" + order.getTotalAmount() : "N/A";
            payCell.addElement(new Paragraph("Value: " + totalAmt, boldFont));
            payCell.addElement(new Paragraph("Status: " + (order.getShipmentStatus() != null ? order.getShipmentStatus() : "BOOKED"), smallFont));
            specsTable.addCell(payCell);

            PdfPCell sCell = new PdfPCell(specsTable);
            sCell.setPadding(4f);
            sCell.setBorder(Rectangle.BOTTOM);
            sCell.setBorderWidth(1f);
            masterTable.addCell(sCell);

            // 5. Items Summary
            PdfPCell itemsCell = new PdfPCell();
            itemsCell.setBorder(Rectangle.BOTTOM);
            itemsCell.setBorderWidth(1f);
            itemsCell.setPadding(4f);
            itemsCell.addElement(new Paragraph("ITEM DETAILS / CONTENTS:", subHeaderFont));

            List<OrderItem> items = order.getItems();
            if (items != null && !items.isEmpty()) {
                StringBuilder itemsStr = new StringBuilder();
                for (int i = 0; i < Math.min(items.size(), 3); i++) {
                    OrderItem item = items.get(i);
                    String pName = item.getProduct() != null ? item.getProduct().getName() : "Item";
                    if (pName.length() > 30) pName = pName.substring(0, 27) + "...";
                    if (itemsStr.length() > 0) itemsStr.append(", ");
                    itemsStr.append(pName).append(" (x").append(item.getQuantity()).append(")");
                }
                if (items.size() > 3) {
                    itemsStr.append(" +").append(items.size() - 3).append(" more");
                }
                itemsCell.addElement(new Paragraph(itemsStr.toString(), regularFont));
            } else {
                itemsCell.addElement(new Paragraph("E-Commerce Parcel Goods", regularFont));
            }
            masterTable.addCell(itemsCell);

            // 6. Return / Ship From Address
            PdfPCell returnCell = new PdfPCell();
            returnCell.setBorder(Rectangle.NO_BORDER);
            returnCell.setPadding(4f);
            returnCell.addElement(new Paragraph("RETURN / SHIP FROM:", subHeaderFont));
            returnCell.addElement(new Paragraph("Lemon House Dispatch Hub, New Delhi - 110001", smallFont));
            returnCell.addElement(new Paragraph("Email: support@lemonhousecraft.in | Web: www.lemonhousecraft.in", smallFont));
            masterTable.addCell(returnCell);

            document.add(masterTable);
            document.close();

            log.info("Generated standard shipping label PDF for order ID: {}, AWB: {}", order.getId(), awb);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF shipping label for order {}: {}", order.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate shipping label PDF: " + e.getMessage(), e);
        }
    }
}
