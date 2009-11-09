package org.esa.beam.gpf.common.mosaic;

import com.bc.ceres.glevel.MultiLevelImage;
import com.bc.jexp.ParseException;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.CrsGeoCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.dataop.barithm.BandArithmetic;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProducts;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.jai.ResolutionLevel;
import org.esa.beam.jai.VirtualBandOpImage;
import org.geotools.geometry.Envelope2D;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.AddCollectionDescriptor;
import javax.media.jai.operator.FormatDescriptor;
import javax.media.jai.operator.MosaicDescriptor;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Marco Peters
 * @since BEAM 4.7
 */
@OperatorMetadata(alias = "Mosaic",
                  version = "1.0",
                  authors = "Marco Peters, Ralf Quast, Marco Zühlke",
                  copyright = "(c) 2009 by Brockmann Consult",
                  description = "Creates a mosaic product out of a set of source products.",
                  internal = true)
public class MosaicOp extends Operator {

    @SourceProducts(count = -1, description = "The source products to be used for mosaic.")
    private Product[] sourceProducts;

    @TargetProduct
    Product targetProduct;

    @Parameter(itemAlias = "variable", description = "Specifies the bands in the target product.")
    Variable[] outputVariables;

    @Parameter(itemAlias = "condition", description = "Specifies valid pixels considered in the target product.")
    Condition[] conditions;

    @Parameter(description = "Specifies the way how conditions are combined.", defaultValue = "OR",
               valueSet = {"OR", "AND"})
    String combine;

    @Parameter(description = "Specifies whether the target product shall be updated.")
    boolean update;

    @Parameter(alias = "epsgCode", description = "Specifies the coordinate reference system of the target product.",
               defaultValue = "EPSG:4326")
    String crsCode;

    @Parameter(alias = "resampling", label = "Resampling Method", description = "The method used for resampling.",
               valueSet = {"Nearest", "Bilinear", "Bicubic"}, defaultValue = "Nearest")
    String resamplingName;

    @Parameter(description = "Specifies the boundary of the target product in map units.")
    GeoBounds boundary;

    @Parameter(description = "Size of a pixel in X-direction in map units.")
    double pixelSizeX;
    @Parameter(description = "Size of a pixel in Y-direction in map units.")
    double pixelSizeY;

    private CoordinateReferenceSystem targetCRS;
    private Product[] reprojectedProducts;


    @Override
    public void initialize() throws OperatorException {
        targetProduct = createTargetProduct();
        reprojectedProducts = createReprojectedProducts();

        // for each variable and each product one 'alpha' image is created.
        // the alpha value for a pixel is either 0.0 or 1.0
        List<List<PlanarImage>> alphaImageList = createAlphaImages();

        // for each variable and each product one 'source' image is created.
        List<List<RenderedImage>> sourceImageList = createSourceImages();

        List<RenderedImage> mosaicImageList = createMosaicImages(sourceImageList, alphaImageList);

        final List<RenderedImage> variableCountImageList = createVariableCountImages(alphaImageList);
        addBands(targetProduct, mosaicImageList, variableCountImageList);
    }

    private List<RenderedImage> createVariableCountImages(List<List<PlanarImage>> alphaImageList) {
        List<RenderedImage> variableCountImageList = new ArrayList<RenderedImage>(outputVariables.length);
        for (List<PlanarImage> variableAlphaImageList : alphaImageList) {
            final RenderedImage countFloatImage = AddCollectionDescriptor.create(variableAlphaImageList, null);
            variableCountImageList.add(FormatDescriptor.create(countFloatImage, DataBuffer.TYPE_INT, null));
        }
        return variableCountImageList;
    }

    private List<List<PlanarImage>> createAlphaImages() {
        final List<List<PlanarImage>> alphaImageList = new ArrayList<List<PlanarImage>>(outputVariables.length);
        for (Variable outputVariable : outputVariables) {
            final ArrayList<PlanarImage> list = new ArrayList<PlanarImage>(reprojectedProducts.length);
            alphaImageList.add(list);
            for (Product product : reprojectedProducts) {
                final String validMaskExpression;
                try {
                    validMaskExpression = createValidMaskExpression(product, outputVariable.expression);
                } catch (ParseException e) {
                    throw new OperatorException(e);
                }
                final StringBuilder combinedExpression = new StringBuilder(validMaskExpression);
                if (conditions != null && conditions.length > 0) {
                    combinedExpression.append(" && (");
                    for (int i = 0; i < conditions.length; i++) {
                        Condition condition = conditions[i];
                        if (i != 0) {
                            combinedExpression.append(" ").append(combine).append(" ");
                        }
                        combinedExpression.append(condition.expression);
                    }
                    combinedExpression.append(")");
                }
                list.add(createExpressionImage(combinedExpression.toString(), product));
            }

        }
        return alphaImageList;
    }

    private static String createValidMaskExpression(Product product, final String expression) throws ParseException {
        return BandArithmetic.getValidMaskExpression(expression, new Product[]{product}, 0, null);
    }

    private List<RenderedImage> createMosaicImages(List<List<RenderedImage>> sourceImageList,
                                                   List<List<PlanarImage>> alphaImageList) {
        final List<RenderedImage> mosaicImages = new ArrayList<RenderedImage>(sourceImageList.size());
        for (int i = 0; i < sourceImageList.size(); i++) {
            final PlanarImage[] sourceAlphas = alphaImageList.get(i).toArray(
                    new PlanarImage[alphaImageList.size()]);
            final List<RenderedImage> sourceImages = sourceImageList.get(i);
            final RenderedImage[] renderedImages = sourceImages.toArray(new RenderedImage[sourceImages.size()]);
            // we don't need ROIs, cause they are not considered by MosaicDescriptor when sourceAlphas are given
            mosaicImages.add(MosaicDescriptor.create(renderedImages, MosaicDescriptor.MOSAIC_TYPE_BLEND,
                                                     sourceAlphas, null, null, null, null));
        }

        return mosaicImages;
    }

    private void addBands(Product product, List<RenderedImage> bandImages, List<RenderedImage> variableCountImageList) {
        for (int i = 0; i < outputVariables.length; i++) {
            Variable outputVariable = outputVariables[i];
            final String countBandName = String.format("%s_count", outputVariable.name);

            Band band = product.addBand(outputVariable.name, ProductData.TYPE_FLOAT32);
            band.setDescription(outputVariable.expression);
            band.setSourceImage(bandImages.get(i));
            band.setValidPixelExpression(String.format("%s > 0", countBandName));

            Band countBand = product.addBand(countBandName, ProductData.TYPE_INT32);
            countBand.setDescription(String.format("Count of %s", outputVariable.name));
            countBand.setSourceImage(variableCountImageList.get(i));
        }

        if (conditions != null) {
            for (Condition condition : conditions) {
                if (condition.output) {
                    Band band = product.addBand(condition.name, ProductData.TYPE_INT32);
                    band.setDescription(condition.expression);
                    // The sum of all conditions of all sources is created.
                    // 1.0 indicates condition is true and 0.0 indicates false.
                    final RenderedOp sumImage = createConditionSumImage(condition);
                    final RenderedOp reformatedImage = FormatDescriptor.create(sumImage, DataBuffer.TYPE_INT, null);
                    band.setSourceImage(reformatedImage);
                }
            }
        }
    }

    private RenderedOp createConditionSumImage(Condition condition) {
        final ArrayList<RenderedImage> renderedImageList = new ArrayList<RenderedImage>(reprojectedProducts.length);
        for (Product reprojectedProduct : reprojectedProducts) {
            // the condition images are used as sourceAlpha parameter for MosaicOpImage, they have to have the same
            // data type as the source images. That's why we use normal expression images with data type FLOAT32.
            renderedImageList.add(createExpressionImage(condition.expression, reprojectedProduct));
        }
        return AddCollectionDescriptor.create(renderedImageList, null);
    }


    private List<List<RenderedImage>> createSourceImages() {
        List<List<RenderedImage>> conditionImageList = new ArrayList<List<RenderedImage>>(outputVariables.length);
        for (Variable variable : outputVariables) {
            final ArrayList<RenderedImage> renderedImageList = new ArrayList<RenderedImage>(reprojectedProducts.length);
            conditionImageList.add(renderedImageList);
            for (Product reprojectedProduct : reprojectedProducts) {
                renderedImageList.add(createExpressionImage(variable.expression, reprojectedProduct));
            }
        }
        return conditionImageList;
    }

    private PlanarImage createExpressionImage(final String expression, Product product) {
        final MultiLevelImage sourceImage = product.getBandAt(0).getSourceImage();
        final ResolutionLevel resolutionLevel = ResolutionLevel.create(sourceImage.getModel(), 0);
        final float fillValue = 0.0f;
        return VirtualBandOpImage.create(expression, ProductData.TYPE_FLOAT32, fillValue, product, resolutionLevel);
    }

    private Product[] createReprojectedProducts() {
        Product[] reprojProducts = new Product[sourceProducts.length];
        final HashMap<String, Object> projParameters = createProjectionParameters();
        for (int i = 0; i < sourceProducts.length; i++) {
            HashMap<String, Product> projProducts = new HashMap<String, Product>();
            projProducts.put("source", sourceProducts[i]);
            projProducts.put("collocate", targetProduct);
            reprojProducts[i] = GPF.createProduct("Reproject", projParameters, projProducts);
        }
        return reprojProducts;
    }

    private HashMap<String, Object> createProjectionParameters() {
        HashMap<String, Object> projParameters = new HashMap<String, Object>();
        projParameters.put("resamplingName", resamplingName);
        projParameters.put("includeTiePointGrids",
                           true);  // ensure tie-points are reprojected - don't relay on default value
        return projParameters;
    }


    private Product createTargetProduct() {
        Product product;
        try {
            targetCRS = CRS.decode(crsCode, true);
            Rectangle2D.Double rect = new Rectangle2D.Double();
            rect.setFrameFromDiagonal(boundary.west, boundary.north, boundary.east, boundary.south);
            Envelope envelope = CRS.transform(new Envelope2D(DefaultGeographicCRS.WGS84, rect), targetCRS);
            int width = (int) (envelope.getSpan(0) / pixelSizeX);
            int height = (int) (envelope.getSpan(1) / pixelSizeY);
            final AffineTransform mapTransform = new AffineTransform();
            mapTransform.translate(boundary.west, boundary.north);
            mapTransform.scale(pixelSizeX, -pixelSizeY);
            mapTransform.translate(-0.5, -0.5);
            CrsGeoCoding geoCoding = new CrsGeoCoding(targetCRS, new Rectangle(0, 0, width, height),
                                                      mapTransform);
            product = new Product("mosaic", "BEAM_MOSAIC", width, height);
            product.setGeoCoding(geoCoding);
        } catch (Exception e) {
            throw new OperatorException(e);
        }

        return product;
    }

    public static class Variable {

        @Parameter(description = "The name of the variable.")
        String name;
        @Parameter(description = "The expression of the variable.")
        String expression;

        public Variable() {
        }

        Variable(String name, String expression) {
            this.name = name;
            this.expression = expression;
        }
    }

    public static class Condition {

        @Parameter(description = "The name of the condition.")
        String name;
        @Parameter(description = "The expression of the condition.")
        String expression;
        @Parameter(description = "Whether the result of the condition shall be written.")
        boolean output;

    }

    public static class GeoBounds {

        @Parameter(description = "The western longitude.")
        double west;
        @Parameter(description = "The northern latitude.")
        double north;
        @Parameter(description = "The eastern longitude.")
        double east;
        @Parameter(description = "The southern latitude.")
        double south;

        public GeoBounds() {
        }

        GeoBounds(double west, double north, double east, double south) {
            this.west = west;
            this.north = north;
            this.east = east;
            this.south = south;
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(MosaicOp.class);
        }
    }


    private class ConditionImageKey {

    }
}