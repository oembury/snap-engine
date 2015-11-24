/*
 * Copyright (C) 2014 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.snap.dem.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.VirtualBand;
import org.esa.snap.core.dataop.dem.ElevationModel;
import org.esa.snap.core.dataop.resamp.ResamplingFactory;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.dem.dataio.DEMFactory;
import org.esa.snap.dem.dataio.FileElevationModel;
import org.esa.snap.engine_utilities.datamodel.Unit;
import org.esa.snap.engine_utilities.gpf.OperatorUtils;
import org.esa.snap.engine_utilities.gpf.TileGeoreferencing;
import org.esa.snap.engine_utilities.gpf.TileIndex;

import java.awt.Rectangle;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * CreateElevationBandOp adds an elevation band to a product
 */

@OperatorMetadata(alias = "AddElevation",
        category = "Raster/DEM Tools",
        authors = "Jun Lu, Luis Veci",
        copyright = "Copyright (C) 2014 by Array Systems Computing Inc.",
        description = "Creates a DEM band.",
        version = "2.0")
public final class AddElevationOp extends Operator {

    @SourceProduct(alias = "source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The digital elevation model.", defaultValue = "SRTM 3Sec", label = "Digital Elevation Model")
    private String demName = "SRTM 3Sec";

    @Parameter(description = "The elevation band name.", defaultValue = "elevation", label = "Elevation Band Name")
    private String elevationBandName = "elevation";

    @Parameter(description = "The external DEM file.", defaultValue = " ", label = "External DEM")
    private String externalDEM = " ";

    @Parameter(defaultValue = ResamplingFactory.BILINEAR_INTERPOLATION_NAME,
            label = "Resampling Method")
    private String resamplingMethod = ResamplingFactory.BILINEAR_INTERPOLATION_NAME;

    private FileElevationModel fileElevationModel = null;
    private ElevationModel dem = null;
    private Band elevationBand = null;
    private double noDataValue = 0;

    private final Map<Band, Band> sourceRasterMap = new HashMap<Band, Band>(10);

    /**
     * Initializes this operator and sets the one and only target product.
     * <p>The target product can be either defined by a field of type {@link Product} annotated with the
     * {@link TargetProduct TargetProduct} annotation or
     * by calling {@link #setTargetProduct} method.</p>
     * <p>The framework calls this method after it has created this operator.
     * Any client code that must be performed before computation of tile data
     * should be placed here.</p>
     *
     * @throws OperatorException If an error occurs during operator initialisation.
     * @see #getTargetProduct()
     */
    @Override
    public void initialize() throws OperatorException {

        try {

            if (externalDEM != null && !externalDEM.trim().isEmpty()) {

                fileElevationModel = new FileElevationModel(new File(externalDEM), resamplingMethod);
                noDataValue = fileElevationModel.getNoDataValue();
            } else {

                dem = DEMFactory.createElevationModel(demName, resamplingMethod);
                noDataValue = dem.getDescriptor().getNoDataValue();
            }

            createTargetProduct();

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Create target product.
     */
    void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName(),
                sourceProduct.getProductType(),
                sourceProduct.getSceneRasterWidth(),
                sourceProduct.getSceneRasterHeight());
        ProductUtils.copyProductNodes(sourceProduct, targetProduct);

        for (Band band : sourceProduct.getBands()) {
            if (band.getName().equalsIgnoreCase(elevationBandName))
                throw new OperatorException("Band " + elevationBandName + " already exists. Try another name.");
            if (band instanceof VirtualBand) {
                final VirtualBand sourceBand = (VirtualBand) band;
                final VirtualBand targetBand = new VirtualBand(sourceBand.getName(),
                        sourceBand.getDataType(),
                        sourceBand.getRasterWidth(),
                        sourceBand.getRasterHeight(),
                        sourceBand.getExpression());
                ProductUtils.copyRasterDataNodeProperties(sourceBand, targetBand);
                targetProduct.addBand(targetBand);
                sourceRasterMap.put(targetBand, band);
            } else {
                final Band targetBand = ProductUtils.copyBand(band.getName(), sourceProduct, targetProduct, false);
                targetBand.setSourceImage(band.getSourceImage());
                sourceRasterMap.put(targetBand, band);
            }
        }

        elevationBand = targetProduct.addBand(elevationBandName, ProductData.TYPE_FLOAT32);
        elevationBand.setNoDataValue(noDataValue);
        elevationBand.setNoDataValueUsed(true);
        elevationBand.setUnit(Unit.METERS);
        elevationBand.setDescription(dem.getDescriptor().getName());
    }

    /**
     * Called by the framework in order to compute a tile for the given target band.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetBand The target band.
     * @param targetTile The current tile associated with the target band to be computed.
     * @param pm         A progress monitor which should be used to determine computation cancelation requests.
     * @throws OperatorException If an error occurs during computation of the target raster.
     */
    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        try {
            if (targetBand == elevationBand) {
                final Rectangle targetRectangle = targetTile.getRectangle();
                final int x0 = targetRectangle.x;
                final int y0 = targetRectangle.y;
                final int w = targetRectangle.width;
                final int h = targetRectangle.height;
                final ProductData trgData = targetTile.getDataBuffer();

                final TileGeoreferencing tileGeoRef = new TileGeoreferencing(targetProduct, x0, y0, w, h);

                final double demNoDataValue = dem.getDescriptor().getNoDataValue();
                final double[][] localDEM = new double[h + 2][w + 2];
                DEMFactory.getLocalDEM(
                        dem, demNoDataValue, resamplingMethod, tileGeoRef, x0, y0, w, h, sourceProduct, true, localDEM);

                final TileIndex trgIndex = new TileIndex(targetTile);

                final int maxX = x0 + w;
                final int maxY = y0 + h;
                for (int y = y0; y < maxY; ++y) {
                    final int yy = y - y0 + 1;
                    trgIndex.calculateStride(y);
                    for (int x = x0; x < maxX; ++x) {

                        trgData.setElemDoubleAt(trgIndex.getIndex(x), localDEM[yy][x - x0 + 1]);
                    }
                }
            }
        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.snap.core.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     *
     * @see OperatorSpi#createOperator()
     * @see OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(AddElevationOp.class);
        }
    }
}
