package com.mes.m14a.listeners;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ISuiteResult;
import org.testng.ITestContext;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes an .xlsx report at the end of every suite. Output location:
 * {@code reports/M14a-API-TestReport-yyyyMMdd-HHmmss.xlsx}
 *
 * Sheets:
 *  - Summary  : per-suite + per-status totals
 *  - Results  : one row per test method with TC ID, class, method, group,
 *               status, duration, description, and failure message.
 */
public class ExcelReportListener implements ISuiteListener {

    private static final Logger log = LogManager.getLogger(ExcelReportListener.class);
    private static final Pattern TC_ID = Pattern.compile("TC_[A-Z0-9_]+");

    @Override
    public void onFinish(ISuite suite) {
        try {
            File outFile = buildOutputPath(suite.getName());
            try (Workbook wb = new XSSFWorkbook()) {
                Styles styles = new Styles(wb);
                writeResultsSheet(wb, suite, styles);
                writeSummarySheet(wb, suite, styles);
                try (FileOutputStream out = new FileOutputStream(outFile)) {
                    wb.write(out);
                }
            }
            log.info("Excel report written: {}", outFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write Excel report", e);
        }
    }

    // ---------- sheets ----------

    private void writeResultsSheet(Workbook wb, ISuite suite, Styles s) {
        Sheet sh = wb.createSheet("Results");
        String[] headers = {
                "TC ID", "Class", "Method", "Group", "Status",
                "Duration (ms)", "Description",
                "API URL", "Token (Hwd)",
                "Request API", "Response API",
                "Expected Result", "Actual Result",
                "Failure Message"
        };
        writeHeader(sh, headers, s);

        int rowIdx = 1;
        for (ISuiteResult sr : suite.getResults().values()) {
            ITestContext ctx = sr.getTestContext();
            rowIdx = writeResults(sh, rowIdx, ctx.getPassedTests().getAllResults(), "PASS", s);
            rowIdx = writeResults(sh, rowIdx, ctx.getFailedTests().getAllResults(), "FAIL", s);
            rowIdx = writeResults(sh, rowIdx, ctx.getSkippedTests().getAllResults(), "SKIP", s);
        }
        int[] widths = {18, 28, 32, 12, 8, 14, 50, 50, 40, 80, 80, 40, 40, 60};
        for (int c = 0; c < widths.length && c < headers.length; c++) sh.setColumnWidth(c, widths[c] * 256);
        sh.createFreezePane(0, 1);
    }

    private int writeResults(Sheet sh, int startRow,
                             java.util.Collection<ITestResult> results,
                             String status, Styles st) {
        int r = startRow;
        for (ITestResult t : results) {
            ITestNGMethod m = t.getMethod();
            Row row = sh.createRow(r++);
            row.setHeightInPoints(60);

            row.createCell(0).setCellValue(extractTcId(m.getDescription()));
            row.createCell(1).setCellValue(t.getTestClass().getRealClass().getSimpleName());
            row.createCell(2).setCellValue(m.getMethodName());
            row.createCell(3).setCellValue(String.join(",", Arrays.asList(m.getGroups())));

            Cell statusCell = row.createCell(4);
            statusCell.setCellValue(status);
            statusCell.setCellStyle(st.forStatus(status));

            row.createCell(5).setCellValue(t.getEndMillis() - t.getStartMillis());

            putWrapped(row, 6,  nullSafe(m.getDescription()),          st.wrap);
            putWrapped(row, 7,  attr(t, "urls"),                       st.wrap);
            putWrapped(row, 8,  attr(t, "tokens"),                     st.wrap);
            putWrapped(row, 9,  truncate(attr(t, "requests"),  16000), st.wrap);
            putWrapped(row, 10, truncate(attr(t, "responses"), 16000), st.wrap);
            putWrapped(row, 11, attr(t, "expected"),                   st.wrap);
            putWrapped(row, 12, attr(t, "actual"),                     st.wrap);
            putWrapped(row, 13, t.getThrowable() == null ? "" : t.getThrowable().getMessage(), st.wrap);
        }
        return r;
    }

    private static void putWrapped(Row row, int col, String value, CellStyle wrap) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(wrap);
    }

    private static String attr(ITestResult t, String key) {
        Object v = t.getAttribute(key);
        return v == null ? "" : v.toString();
    }

    /** Excel hard limit per cell is 32,767 chars; trim well below to be safe. */
    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…[truncated]";
    }

    private void writeSummarySheet(Workbook wb, ISuite suite, Styles s) {
        Sheet sh = wb.createSheet("Summary");

        Row title = sh.createRow(0);
        Cell tc = title.createCell(0);
        tc.setCellValue("M14a API Test Report  -  " +
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()));
        tc.setCellStyle(s.title);
        sh.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

        String[] headers = {"Suite / Test", "Total", "Passed", "Failed", "Skipped"};
        Row hdr = sh.createRow(2);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(s.header);
        }

        int r = 3;
        int sumPass = 0, sumFail = 0, sumSkip = 0;

        for (ISuiteResult sr : suite.getResults().values()) {
            ITestContext ctx = sr.getTestContext();
            int p = ctx.getPassedTests().size();
            int f = ctx.getFailedTests().size();
            int sk = ctx.getSkippedTests().size();
            sumPass += p; sumFail += f; sumSkip += sk;

            Row row = sh.createRow(r++);
            row.createCell(0).setCellValue(ctx.getName());
            row.createCell(1).setCellValue(p + f + sk);
            row.createCell(2).setCellValue(p);
            row.createCell(3).setCellValue(f);
            row.createCell(4).setCellValue(sk);
        }

        Row total = sh.createRow(r + 1);
        Cell tlabel = total.createCell(0);
        tlabel.setCellValue("TOTAL");
        tlabel.setCellStyle(s.bold);
        total.createCell(1).setCellValue(sumPass + sumFail + sumSkip);
        Cell pc = total.createCell(2); pc.setCellValue(sumPass); pc.setCellStyle(s.passBold);
        Cell fc = total.createCell(3); fc.setCellValue(sumFail); fc.setCellStyle(s.failBold);
        Cell kc = total.createCell(4); kc.setCellValue(sumSkip); kc.setCellStyle(s.skipBold);

        for (int c = 0; c < headers.length; c++) sh.autoSizeColumn(c);
    }

    // ---------- helpers ----------

    private static File buildOutputPath(String suiteName) {
        File dir = new File("reports");
        if (!dir.exists()) dir.mkdirs();
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        String safe = suiteName == null ? "Suite" : suiteName.replaceAll("[^A-Za-z0-9_-]", "_");
        return new File(dir, safe + "-TestReport-" + stamp + ".xlsx");
    }

    private static String extractTcId(String description) {
        if (description == null) return "";
        Matcher m = TC_ID.matcher(description);
        return m.find() ? m.group() : "";
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static void writeHeader(Sheet sh, String[] headers, Styles s) {
        Row hdr = sh.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(s.header);
        }
    }

    // ---------- cell styles ----------

    private static final class Styles {
        final CellStyle header, title, bold, wrap;
        final CellStyle pass, fail, skip;
        final CellStyle passBold, failBold, skipBold;

        Styles(Workbook wb) {
            wrap = wb.createCellStyle();
            wrap.setWrapText(true);
            wrap.setVerticalAlignment(VerticalAlignment.TOP);

            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            header = wb.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);

            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            title = wb.createCellStyle();
            title.setFont(titleFont);

            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            bold = wb.createCellStyle();
            bold.setFont(boldFont);

            pass = wb.createCellStyle();
            pass.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            pass.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            fail = wb.createCellStyle();
            fail.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            fail.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            skip = wb.createCellStyle();
            skip.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            skip.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            passBold = cloneWithBold(wb, pass, boldFont);
            failBold = cloneWithBold(wb, fail, boldFont);
            skipBold = cloneWithBold(wb, skip, boldFont);
        }

        CellStyle forStatus(String s) {
            switch (s) {
                case "PASS": return pass;
                case "FAIL": return fail;
                case "SKIP": return skip;
                default:     return null;
            }
        }

        private static CellStyle cloneWithBold(Workbook wb, CellStyle src, Font boldFont) {
            CellStyle cs = wb.createCellStyle();
            cs.cloneStyleFrom(src);
            cs.setFont(boldFont);
            return cs;
        }
    }
}
