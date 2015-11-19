/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.snap.core.gpf.doclet;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.Doclet;
import com.sun.javadoc.LanguageVersion;
import com.sun.javadoc.RootDoc;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.OperatorSpiRegistry;
import org.esa.snap.core.gpf.descriptor.OperatorDescriptor;

// This Main must be started with ceres launcher. Otherwise not all dependencies are on the classpath.
// Main class: com.bc.ceres.launcher.Launcher
// VM options: -Dceres.context=beam -Dbeam.mainClass=OperatorDoclet


/**
 * A doclet which scans the classpath for GPF operators and creates
 * associated documentation from the {@link OperatorDescriptor} retrieved via the {@link OperatorSpi}.
 * <p>
 * This Doclet can be called on Windows from the command line
 * by the following instruction.
 * <b>NOTE:</b> You have to adopt the pathes to your needs.
 * <p>
 * <pre>
 *  SET DocletClassName=OperatorDoclet
 *  SET SourcePath=.\beam-gpf\src\main\java
 *  SET ClassPath=.\beam-gpf\target\classes
 * </pre>
 * <p>
 * <pre>
 * javadoc -doclet "%DocletClassName%" -docletpath "%DocletPath%" ^
 *         -sourcepath "%SourcePath%" -classpath "%ClassPath%" ^
 *         org.esa.snap.gpf.operators.std
 * </pre>
 *
 * @author Norman Fomferra
 * @version $Revision$ $Date$
 */
public class OperatorDoclet extends Doclet {

    static String format;

    public static void main(String[] args) {
        format = "html";
//        if (args.length == 0) {
//            format = "console";
//        } else if (args.length == 1) {
//            format = args[0];
//        } else {
//            System.out.println("Usage: OperatorDoclet [ console | html ]");
//            System.exit(1);
//        }
        String basePath = "C:/Users/muhammad.bc/IdeaProjects/senbox/";
        com.sun.tools.javadoc.Main.main(new String[]{
                "-doclet", OperatorDoclet.class.getName(),
                "-sourcepath", "" +
                    basePath + "snap-engine/snap-dem/src/main/java;" +
                    basePath + "snap-engine/snap-engine-utilities/src/main/java;" +
                    basePath + "snap-engine/snap-python/src/main/java;" +
                    basePath + "snap-engine/snap-sta/src/main/java;" +
                    basePath + "snap-engine/snap-binning/src/main/java;" +
                    basePath + "snap-engine/snap-gpf/src/main/java;" +
                    basePath + "snap-engine/snap-cluster-analysis/src/main/java;" +
                    basePath + "snap-engine/snap-collocation/src/main/java;" +
                    basePath + "snap-engine/snap-pixel-extraction/src/main/java;" +
                    basePath + "snap-engine/snap-statistics/src/main/java;" +
                    basePath + "snap-engine/snap-temporal-percentile/src/main/java;" +
                    basePath + "snap-engine/snap-ndvi/src/main/java;" +
                    basePath + "snap-engine/snap-unmix/src/main/java;" +
                    basePath + "s3tbx/s3tbx-meris-smac/src/main/java;" +
                    basePath + "s3tbx/s3tbx-aatsr-sst/src/main/java;" +
                    basePath + "s3tbx/s3tbx-meris-cloud/src/main/java;" +
                    basePath + "s3tbx/s3tbx-meris-flhmci/src/main/java;" +
                    basePath + "s3tbx/s3tbx-meris-radiometry/src/main/java;" +
                    basePath + "s3tbx/s3tbx-meris-ops/src/main/java;" +
                    basePath + "s3tbx/s3tbx-slstr-pdu-stitching/src/main/java;",

//                "-classpath", "" +
//                    "./snap-engine/snap-dem/target/snap-dem-2.0.0-SNAPSHOT.jar;"+
//                    "./snap-engine/snap-engine-utilities/target/snap-engine-utilities-2.0.0-SNAPSHOT.jar;" +
//                    "./snap-engine/snap-python/target/snap-python-2.0.0-SNAPSHOT.jar;" +
//                    "./snap-engine/snap-sta/target/snap-sta-2.0.0-SNAPSHOT.jar;" +
//                    "./snap-engine/snap-binning/target/snap-binning-2.0.0-SNAPSHOT.jar;" +
//                    "./snap-engine/snap-gpf/target/snap-gpf-2.0.0-SNAPSHOT.jar;" +
//                    "./snap-engine/snap-cluster-analysis/target/snap-cluster-analysis-2.0.0-SNAPSHOT.jar;" +
//                    "./snap-engine/snap-collocation/target/snap-collocation-2.0.0-SNAPSHOT.jar;" +
//                    "./snap-pixel-extraction/target/snap-pixel-extraction-2.0.0-SNAPSHOT.jar;" +
//                    "./snap-engine/snap-statistics/target/snap-statistics-2.0.0-SNAPSHOT.jar;" +
//                    "./snap-engine/snap-temporal-percentile/target/snap-temporal-percentile-2.0.0-SNAPSHOT.jar;" +
//                    "./snap-engine/snap-ndvi/target/snap-ndvi-2.0.0-SNAPSHOT.jar;" +
//                    "./snap-engine/snap-unmix/target/snap-unmix-2.0.0-SNAPSHOT.jar;" +
//                    "./snap-engine/ceres-core/target/ceres-core-2.0.0-SNAPSHOT.jar;"+
//                    "./s3tbx/s3tbx-meris-smac/target/s3tbx-meris-smac-2.0.0-SNAPSHOT-sources.jar;" +
//                    "./s3tbx/s3tbx-meris-cloud/target/s3tbx-meris-cloud-2.0.0-SNAPSHOT.jar;" +
//                    "./s3tbx/s3tbx-meris-ops/target/s3tbx-meris-ops-2.0.0-SNAPSHOT.jar"+
//                    "./snap-engine/snap-core/target/snap-core-2.0.0-SNAPSHOT.jar;" +
//                    "./s3tbx/s3tbx-slstr-pdu-stitching/target/s3tbx-slstr-pdu-stitching-2.0.0-SNAPSHOT.jar;" ,


                "org.esa.snap.dem.gpf",
                "org.esa.snap.engine_utilities.gpf",
                "org.esa.snap.python.gpf",
                "org.esa.snap.core.gpf.common",
                "org.esa.snap.core.gpf.common.reproject",
                "org.esa.snap.binning.operator",
                "org.esa.snap.cluster",
                "org.esa.snap.collocation",
                "org.esa.snap.pixex",
                "org.esa.snap.statistics",
                "org.esa.snap.statistics.percentile.interpolated",
                "org.esa.snap.ndvi",
                "org.esa.snap.unmixing",
                "org.esa.s3tbx.operator.cloud",
                "org.esa.s3tbx.aatsr.sst",
                "org.esa.s3tbx.smac",
                "org.esa.s3tbx.processor.flh_mci",
                "org.esa.s3tbx.meris.radiometry",
                "org.esa.s3tbx.slstr.pdu.stitching"
        });
    }

    public static boolean start(RootDoc root) {
        OperatorHandler operatorHandler;
        if ("console".equalsIgnoreCase(format)) {
            operatorHandler = new OperatorHandlerConsole();
        } else if ("html".equalsIgnoreCase(format)) {
            operatorHandler = new OperatorHandlerHtml();
        } else {
            throw new RuntimeException("Illegal output format: " + format);
        }

        try {
            operatorHandler.start(root);
        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            throw new RuntimeException(t);
        }
        OperatorSpiRegistry operatorSpiRegistry = GPF.getDefaultInstance().getOperatorSpiRegistry();
        operatorSpiRegistry.loadOperatorSpis();

        ClassDoc[] classDocs = root.classes();
        for (ClassDoc classDoc : classDocs) {
            if (classDoc.subclassOf(root.classNamed(Operator.class.getName()))) {
                try {
                    System.out.println("Processing " + classDoc.typeName() + "...");
                    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                    Class<? extends Operator> type = (Class<? extends Operator>) contextClassLoader.loadClass(classDoc.qualifiedTypeName());
                    OperatorSpi operatorSpi = operatorSpiRegistry.getOperatorSpi(OperatorSpi.getOperatorAlias(type));
                    if (operatorSpi != null) {
                        OperatorDescriptor operatorDescriptor = operatorSpi.getOperatorDescriptor();
                        if (!operatorDescriptor.isInternal()) {
                            OperatorDesc operatorDesc = new OperatorDesc(type, classDoc, operatorDescriptor);
                            operatorHandler.processOperator(operatorDesc);
                        } else {
                            System.err.printf("Warning: Skipping %s because it is internal.%n", classDoc.typeName());
                        }
                    } else {
                        System.err.printf("No SPI found for operator class '%s'.%n", type.getName());
                    }
                } catch (Throwable e) {
                    System.err.println("Error: " + classDoc.typeName() + ": " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        }

        try {
            operatorHandler.stop(root);
        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            throw new RuntimeException(t);
        }

        return true;
    }

    public static int optionLength(String optionName) {
        if (optionName.equals("format")) {
            return 1;
        }
        return 0;
    }

    public static boolean validOptions(String[][] options,
                                       DocErrorReporter docErrorReporter) {
        for (int i = 0; i < options.length; i++) {
            for (int j = 0; j < options[i].length; j++) {
                docErrorReporter.printWarning("options[" + i + "][" + j + "] = " + options[i][j]);
            }
        }
        return true;
    }

    public static LanguageVersion languageVersion() {
        return LanguageVersion.JAVA_1_5;
    }
}
