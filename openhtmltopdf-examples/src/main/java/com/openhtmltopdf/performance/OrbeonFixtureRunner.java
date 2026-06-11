package com.openhtmltopdf.performance;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FSFontUseCase;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.PageSizeUnits;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;

/**
 * Benchmark runner for the Orbeon Forms fixture (see
 * src/main/resources/benchmark/orbeon/README.md and
 * https://github.com/orbeon/orbeon-forms/issues/7681).
 *
 * <p>Renders the captured Orbeon Form Runner XHTML the same way Orbeon's
 * XHTMLToPDFProcessor does (page size 8.5x11in, 14 dots per pixel, Inter as
 * final-fallback font) and reports wall times for the layout() and
 * createPDF() phases separately.</p>
 *
 * <p>Run from the repo root or the openhtmltopdf-examples directory:</p>
 * <pre>
 * mvn -q -pl openhtmltopdf-examples -am install -DskipTests
 * mvn -q -pl openhtmltopdf-examples exec:java \
 *     -Dexec.mainClass=com.openhtmltopdf.performance.OrbeonFixtureRunner
 * </pre>
 *
 * <p>Options: --warmup N (default 5), --iterations N (default 10),
 * --wait (sleep 20s before the measured iterations so a profiler can be
 * attached). The fixture directory can be overridden with
 * -Dorbeon.fixture.dir=/path/to/dir.</p>
 */
public class OrbeonFixtureRunner {

    private static final String FIXTURE_NAME = "controls.xhtml";

    static class Result {
        final long layoutNanos;
        final long createPdfNanos;
        final int pageCount;
        final byte[] pdf;

        Result(long layoutNanos, long createPdfNanos, int pageCount, byte[] pdf) {
            this.layoutNanos = layoutNanos;
            this.createPdfNanos = createPdfNanos;
            this.pageCount = pageCount;
            this.pdf = pdf;
        }
    }

    public static void main(String... args) throws Exception {
        int warmup = 5;
        int iterations = 10;
        boolean wait = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
            case "--warmup":
                warmup = Integer.parseInt(args[++i]);
                break;
            case "--iterations":
                iterations = Integer.parseInt(args[++i]);
                break;
            case "--wait":
                wait = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        File fixtureDir = findFixtureDir();
        File fixtureFile = new File(fixtureDir, FIXTURE_NAME);
        File fontFile = new File(fixtureDir, "Inter-Medium.ttf");

        System.out.println("Fixture: " + fixtureFile.getAbsolutePath());
        System.out.println("JVM: " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));

        XRLog.setLoggingEnabled(false);

        System.out.println("Warmup (" + warmup + " iterations)...");
        for (int i = 0; i < warmup; i++) {
            render(fixtureFile, fontFile);
        }

        if (wait) {
            System.out.println("Process: " + java.lang.management.ManagementFactory.getRuntimeMXBean().getName());
            System.out.println("Attach your profiler now, measured iterations start in 20s...");
            Thread.sleep(20_000);
        }

        System.out.println("Measuring (" + iterations + " iterations)...");
        List<Result> results = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            Result r = render(fixtureFile, fontFile);
            results.add(r);
            System.out.printf("  #%-2d layout %6.1f ms  createPDF %6.1f ms  total %6.1f ms  (%d pages)%n",
                    i + 1, ms(r.layoutNanos), ms(r.createPdfNanos), ms(r.layoutNanos + r.createPdfNanos),
                    r.pageCount);
        }

        report("layout   ", results.stream().mapToLong(r -> r.layoutNanos).toArray());
        report("createPDF", results.stream().mapToLong(r -> r.createPdfNanos).toArray());
        report("total    ", results.stream().mapToLong(r -> r.layoutNanos + r.createPdfNanos).toArray());

        Files.createDirectories(Paths.get("target/test/profiling/"));
        Files.write(Paths.get("target/test/profiling/orbeon-fixture.pdf"),
                results.get(results.size() - 1).pdf);
        System.out.println("PDF written to target/test/profiling/orbeon-fixture.pdf");
    }

    static Result render(File fixtureFile, File fontFile) throws Exception {
        Document doc = parse(fixtureFile);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(0xffff);
        PdfRendererBuilder builder = new PdfRendererBuilder();

        // Mirror Orbeon's XHTMLToPDFProcessor configuration
        builder.useDefaultPageSize(8.5f, 11f, PageSizeUnits.INCHES);
        builder.useFont(
                (FSSupplier<InputStream>) () -> {
                    try {
                        return new FileInputStream(fontFile);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                "Inter", 500, FontStyle.NORMAL, true,
                EnumSet.of(FSFontUseCase.FALLBACK_FINAL));
        builder.withW3cDocument(doc, fixtureFile.toURI().toString());
        builder.toStream(baos);

        long layoutNanos;
        long createPdfNanos;
        int pageCount;

        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            renderer.getSharedContext().setDotsPerPixel(14);

            long t0 = System.nanoTime();
            renderer.layout();
            long t1 = System.nanoTime();
            renderer.createPDF();
            long t2 = System.nanoTime();

            layoutNanos = t1 - t0;
            createPdfNanos = t2 - t1;
            pageCount = renderer.getRootBox().getLayer().getPages().size();
        }

        return new Result(layoutNanos, createPdfNanos, pageCount, baos.toByteArray());
    }

    static Document parse(File fixtureFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Avoid deferred node expansion so that, as in Orbeon (where the DOM is
        // built from SAX events), DOM access during layout is not skewed by
        // lazy node materialization.
        try {
            factory.setFeature("http://apache.org/xml/features/dom/defer-node-expansion", false);
        } catch (Exception e) {
            // Not a Xerces-based factory; fine.
        }
        return factory.newDocumentBuilder().parse(fixtureFile);
    }

    static File findFixtureDir() {
        String override = System.getProperty("orbeon.fixture.dir");
        if (override != null) {
            return requireDir(new File(override));
        }
        File fromModule = new File("src/main/resources/benchmark/orbeon");
        if (fromModule.isDirectory()) {
            return fromModule;
        }
        return requireDir(new File("openhtmltopdf-examples/src/main/resources/benchmark/orbeon"));
    }

    private static File requireDir(File dir) {
        if (!dir.isDirectory()) {
            throw new IllegalStateException("Fixture directory not found: " + dir.getAbsolutePath()
                    + " (set -Dorbeon.fixture.dir or run from the repo root)");
        }
        return dir;
    }

    private static double ms(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static void report(String label, long[] nanos) {
        java.util.Arrays.sort(nanos);
        double min = ms(nanos[0]);
        double median = ms(nanos[nanos.length / 2]);
        double mean = ms((long) java.util.Arrays.stream(nanos).average().orElse(0));
        System.out.printf("%s  min %7.1f ms  median %7.1f ms  mean %7.1f ms%n", label, min, median, mean);
    }
}
