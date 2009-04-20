package org.esa.beam.glayer;

import com.bc.ceres.binding.ValueContainer;
import com.bc.ceres.binding.ValueModel;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.LayerContext;
import com.bc.ceres.glayer.LayerType;
import com.bc.ceres.glayer.Style;
import com.bc.ceres.grender.Rendering;
import com.bc.ceres.grender.Viewport;
import org.esa.beam.framework.datamodel.Pin;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.PlacemarkDescriptor;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNode;
import org.esa.beam.framework.datamodel.ProductNodeEvent;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.framework.datamodel.ProductNodeListenerAdapter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

public class PlacemarkLayer extends Layer {

    private static final Type LAYER_TYPE = (Type) LayerType.getLayerType(Type.class.getName());

    public static final String PROPERTY_NAME_TEXT_FONT = "text.font";
    public static final String PROPERTY_NAME_TEXT_ENABLED = "text.enabled";
    public static final String PROPERTY_NAME_TEXT_FG_COLOR = "text.fg.color";
    public static final String PROPERTY_NAME_TEXT_BG_COLOR = "text.bg.color";

    public static final boolean DEFAULT_TEXT_ENABLED = false;
    public static final Font DEFAULT_TEXT_FONT = new Font("Helvetica", Font.BOLD, 14);
    public static final Color DEFAULT_TEXT_FG_COLOR = Color.WHITE;
    public static final Color DEFAULT_TEXT_BG_COLOR = Color.BLACK;

    private final MyProductNodeListenerAdapter pnl;
    private Product product;
    private PlacemarkDescriptor placemarkDescriptor;
    private final AffineTransform imageToModelTransform;

    public PlacemarkLayer(Product product, PlacemarkDescriptor placemarkDescriptor,
                          AffineTransform imageToModelTransform) {
        this(LAYER_TYPE, product, placemarkDescriptor, imageToModelTransform);
    }

    protected PlacemarkLayer(Type type, Product product, PlacemarkDescriptor placemarkDescriptor,
                             AffineTransform imageToModelTransform) {
        super(type);
        this.product = product;
        this.placemarkDescriptor = placemarkDescriptor;
        this.imageToModelTransform = new AffineTransform(imageToModelTransform);
        this.pnl = new MyProductNodeListenerAdapter();
        product.addProductNodeListener(pnl);
    }

    @Override
    public void disposeLayer() {
        if (product != null) {
            product.removeProductNodeListener(pnl);
            product = null;
        }
    }

    protected ProductNodeGroup<Pin> getPlacemarkGroup() {
        return placemarkDescriptor.getPlacemarkGroup(getProduct());
    }

    public Product getProduct() {
        return product;
    }

    public PlacemarkDescriptor getPlacemarkDescriptor() {
        return placemarkDescriptor;
    }

    public AffineTransform getImageToModelTransform() {
        return new AffineTransform(imageToModelTransform);
    }

    @Override
    protected void renderLayer(Rendering rendering) {
        Graphics2D g2d = rendering.getGraphics();
        Viewport viewport = rendering.getViewport();
        AffineTransform oldTransform = g2d.getTransform();
        Object oldAntialiasing = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Object oldTextAntialiasing = g2d.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);

        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            final AffineTransform transform = new AffineTransform();
            transform.concatenate(oldTransform);
            transform.concatenate(viewport.getModelToViewTransform());
            transform.concatenate(imageToModelTransform);
            g2d.setTransform(transform);

            ProductNodeGroup<Pin> pinGroup = getPlacemarkGroup();
            Pin[] placemarks = pinGroup.toArray(new Pin[pinGroup.getNodeCount()]);
            for (final Pin placemark : placemarks) {
                final PixelPos pixelPos = placemark.getPixelPos();
                if (pixelPos != null) {
                    g2d.translate(pixelPos.getX(), pixelPos.getY());
                    final double scale = Math.sqrt(Math.abs(g2d.getTransform().getDeterminant()));
                    g2d.scale(1 / scale, 1 / scale);
                    g2d.rotate(viewport.getOrientation());

                    if (placemark.isSelected()) {
                        placemark.getSymbol().drawSelected(g2d);
                    } else {
                        placemark.getSymbol().draw(g2d);
                    }

                    if (isTextEnabled()) {
                        drawTextLabel(g2d, placemark);
                    }

                    g2d.rotate(-viewport.getOrientation());
                    g2d.scale(scale, scale);
                    g2d.translate(-pixelPos.getX(), -pixelPos.getY());
                }
            }
        } finally {
            g2d.setTransform(oldTransform);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldTextAntialiasing);
        }
    }

    private void drawTextLabel(Graphics2D g2d, Pin placemark) {

        final String label = placemark.getLabel();

        g2d.setFont(getTextFont());

        GlyphVector glyphVector = getTextFont().createGlyphVector(g2d.getFontRenderContext(), label);
        Rectangle2D logicalBounds = glyphVector.getLogicalBounds();
        float tx = (float) (logicalBounds.getX() - 0.5 * logicalBounds.getWidth());
        float ty = (float) (1.0 + logicalBounds.getHeight());
        Shape outline = glyphVector.getOutline(tx, ty);

        int[] alphas = new int[]{64, 128, 192, 255};
        for (int i = 0; i < alphas.length; i++) {
            BasicStroke selectionStroke = new BasicStroke((alphas.length - i));
            Color selectionColor = new Color(getTextBgColor().getRed(),
                                             getTextBgColor().getGreen(),
                                             getTextBgColor().getGreen(),
                                             alphas[i]);
            g2d.setStroke(selectionStroke);
            g2d.setPaint(selectionColor);
            g2d.draw(outline);
        }

        g2d.setPaint(getTextFgColor());
        g2d.fill(outline);
    }

    public boolean isTextEnabled() {
        final Style style = getStyle();

        if (style.hasProperty(PROPERTY_NAME_TEXT_ENABLED)) {
            return (Boolean) style.getProperty(PROPERTY_NAME_TEXT_ENABLED);
        }

        return DEFAULT_TEXT_ENABLED;
    }

    public void setTextEnabled(boolean textEnabled) {
        getStyle().setProperty(PROPERTY_NAME_TEXT_ENABLED, textEnabled);
    }

    public Font getTextFont() {
        final Style style = getStyle();

        if (style.hasProperty(PROPERTY_NAME_TEXT_FONT)) {
            return (Font) style.getProperty(PROPERTY_NAME_TEXT_FONT);
        }

        return DEFAULT_TEXT_FONT;
    }

    public void setTextFont(Font font) {
        getStyle().setProperty(PROPERTY_NAME_TEXT_FONT, font);
    }

    public Color getTextFgColor() {
        final Style style = getStyle();

        if (style.hasProperty(PROPERTY_NAME_TEXT_FG_COLOR)) {
            return (Color) style.getProperty(PROPERTY_NAME_TEXT_FG_COLOR);
        }

        return DEFAULT_TEXT_FG_COLOR;
    }

    public void setTextFgColor(Color color) {
        getStyle().setProperty(PROPERTY_NAME_TEXT_FG_COLOR, color);
    }

    public Color getTextBgColor() {
        final Style style = getStyle();

        if (style.hasProperty(PROPERTY_NAME_TEXT_BG_COLOR)) {
            return (Color) style.getProperty(PROPERTY_NAME_TEXT_BG_COLOR);
        }

        return DEFAULT_TEXT_BG_COLOR;
    }

    public void setTextBgColor(Color color) {
        getStyle().setProperty(PROPERTY_NAME_TEXT_BG_COLOR, color);
    }

    public static class Type extends LayerType {

        public static final String PROPERTY_PRODUCT = "product";
        public static final String PROPERTY_PLACEMARK_DESCRIPTOR = "placemarkDescriptor";
        public static final String PROPERTY_IMAGE_TO_MODEL_TRANSFORM = "imageToModelTransform";

        @Override
        public String getName() {
            return "Placemark";
        }

        @Override
        public boolean isValidFor(LayerContext ctx) {
            return true;
        }

        @Override
        public Layer createLayer(LayerContext ctx, ValueContainer configuration) {
            Product product = (Product) configuration.getValue(PROPERTY_PRODUCT);
            PlacemarkDescriptor placemarkDescriptor = (PlacemarkDescriptor) configuration.getValue(
                    PROPERTY_PLACEMARK_DESCRIPTOR);
            AffineTransform imageToModelTransform = (AffineTransform) configuration.getValue(
                    PROPERTY_IMAGE_TO_MODEL_TRANSFORM);
            return new PlacemarkLayer(product, placemarkDescriptor, imageToModelTransform);
        }

        @Override
        public ValueContainer getConfigurationCopy(LayerContext ctx, Layer layer) {
            final PlacemarkLayer placemarkLayer = (PlacemarkLayer) layer;
            final ValueContainer valueContainer = new ValueContainer();
            valueContainer.addModel(createDefaultValueModel(PROPERTY_PRODUCT, placemarkLayer.getProduct()));
            valueContainer.addModel(createDefaultValueModel(PROPERTY_PLACEMARK_DESCRIPTOR,
                                                            placemarkLayer.getPlacemarkDescriptor()));
            valueContainer.addModel(createDefaultValueModel(PROPERTY_IMAGE_TO_MODEL_TRANSFORM,
                                                            placemarkLayer.getImageToModelTransform()));
            return valueContainer;
        }

        @Override
        public ValueContainer getConfigurationTemplate() {
            final ValueModel productModel = createDefaultValueModel(PROPERTY_PRODUCT, Product.class);
            final ValueModel placemarkModel = createDefaultValueModel(PROPERTY_PLACEMARK_DESCRIPTOR,
                                                                      PlacemarkDescriptor.class);
            final ValueModel transformModel = createDefaultValueModel(PROPERTY_IMAGE_TO_MODEL_TRANSFORM,
                                                                      AffineTransform.class);
            final ValueContainer valueContainer = new ValueContainer();
            valueContainer.addModel(productModel);
            valueContainer.addModel(placemarkModel);
            valueContainer.addModel(transformModel);
            return valueContainer;
        }
    }

    private class MyProductNodeListenerAdapter extends ProductNodeListenerAdapter {

        @Override
        public void nodeAdded(ProductNodeEvent event) {
            maybeFireLayerDataChanged(event);
        }

        @Override
        public void nodeRemoved(ProductNodeEvent event) {
            maybeFireLayerDataChanged(event);
        }

        @Override
        public void nodeChanged(ProductNodeEvent event) {
            maybeFireLayerDataChanged(event);
        }

        private void maybeFireLayerDataChanged(ProductNodeEvent event) {
            if (event.getSourceNode() instanceof Pin &&
                !ProductNode.PROPERTY_NAME_OWNER.equals(event.getPropertyName())) {
                final Rectangle2D region = null; // todo - compute region (nf)
                fireLayerDataChanged(region);
            }
        }

    }
}
