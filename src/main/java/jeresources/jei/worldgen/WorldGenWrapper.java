package jeresources.jei.worldgen;

import com.google.common.base.Objects;
import com.mojang.blaze3d.matrix.MatrixStack;
import jeresources.api.conditionals.Conditional;
import jeresources.api.drop.LootDrop;
import jeresources.config.Settings;
import jeresources.entry.WorldGenEntry;
import jeresources.util.Font;
import jeresources.util.RenderHelper;
import jeresources.util.TranslationHelper;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.ITooltipCallback;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class WorldGenWrapper implements IRecipeCategoryExtension, ITooltipCallback<ItemStack> {
    protected static final int X_OFFSET = 49 - 20;
    protected static final int Y_OFFSET = 52;
    protected static final int X_AXIS_SIZE = 100 + 20 + 8;
    protected static final int Y_AXIS_SIZE = 40;

    private final WorldGenEntry worldGenEntry;

    public WorldGenWrapper(WorldGenEntry worldGenEntry) {
        this.worldGenEntry = worldGenEntry;
    }

    public int getLineColor() {
        return this.worldGenEntry.getColour();
    }

    @Override
    public void setIngredients(@Nonnull IIngredients ingredients) {
        ingredients.setInput(VanillaTypes.ITEM, this.worldGenEntry.getBlock());
        ingredients.setOutputs(VanillaTypes.ITEM, this.worldGenEntry.getBlockAndDrops());
    }

    public ItemStack getBlock() {
        return this.worldGenEntry.getBlock();
    }

    public List<ItemStack> getDrops() {
        return this.worldGenEntry.getDrops();
    }

    @Override
    public void drawInfo(int recipeWidth, int recipeHeight, MatrixStack matrixStack, double mouseX, double mouseY) {
        float[] array = this.worldGenEntry.getChances();
        double max = 0;
        for (double d : array)
            if (d > max) max = d;
        double xPrev = X_OFFSET;
        double yPrev = Y_OFFSET;
        double space = X_AXIS_SIZE / ((array.length - 1) * 1D);
        for (int i = 0; i < array.length; i++) {
            double value = array[i];
            double y = Y_OFFSET - ((value / max) * Y_AXIS_SIZE);
            if (i > 0) // Only draw a line after the first element (cannot draw line with only one point)
            {
                double x = xPrev + space;
                RenderHelper.drawLine(matrixStack, xPrev, yPrev, x, y, getLineColor());
                xPrev = x;
            }
            yPrev = y;
        }

        final int xPercents = X_OFFSET - 2;
        final int yPercents = Y_OFFSET - 4;

        final String minPercent = "0%";
        final int minPercentWidth = Font.small.getStringWidth(minPercent);
        Font.small.print(matrixStack, minPercent, xPercents - minPercentWidth, yPercents);
        final String maxPercent = String.format("%.2f", max * 100) + "%";
        final int maxPercentWidth = Font.small.getStringWidth(maxPercent);
        Font.small.print(matrixStack, maxPercent, xPercents - maxPercentWidth, yPercents - Y_AXIS_SIZE);

        final int yLabels = Y_OFFSET + 2;
        final int xLabels = X_OFFSET;

        final int minLabel = this.worldGenEntry.getMinY();
        final int minLabelWidth = Font.small.getStringWidth(String.valueOf(minLabel));
        final int minLabelOffset = xLabels - (minLabelWidth / 2);
        Font.small.print(matrixStack, minLabel, minLabelOffset, yLabels);

        final int maxLabel = this.worldGenEntry.getMaxY();
        final int maxLabelWidth = Font.small.getStringWidth(String.valueOf(maxLabel));
        final int maxLabelOffset = xLabels + X_AXIS_SIZE - (maxLabelWidth / 2);
        Font.small.print(matrixStack, maxLabel, maxLabelOffset, yLabels);

        final int midLabel = (maxLabel + minLabel) / 2;
        final int midLabelWidth = Font.small.getStringWidth(String.valueOf(midLabel));
        final int midLabelOffset = xLabels + (X_AXIS_SIZE / 2) - (midLabelWidth / 2);
        Font.small.print(matrixStack, midLabel, midLabelOffset, yLabels);

        Font.small.print(matrixStack, TranslationHelper.translateAndFormat("jer.worldgen.drops"), WorldGenCategory.X_DROP_ITEM, WorldGenCategory.Y_DROP_ITEM - 8);

        String dimension = TranslationHelper.tryDimensionTranslate(worldGenEntry.getDimension());
        int x = (recipeWidth - Font.normal.getStringWidth(dimension)) / 2;
        Font.normal.print(matrixStack, dimension, x, 0);
    }

    @Override
    public List<ITextComponent> getTooltipStrings(double mouseX, double mouseY) {
        List<ITextComponent> tooltip = new LinkedList<>();
        if (onGraph(mouseX, mouseY))
            tooltip = getLineTooltip(mouseX, tooltip);
        return tooltip;
    }

    @Override
    public void onTooltip(int slotIndex, boolean input, ItemStack ingredient, List<ITextComponent> tooltip) {
        tooltip.addAll(getItemStackTooltip(slotIndex, ingredient));
    }

    private List<ITextComponent> getItemStackTooltip(int slot, ItemStack itemStack) {
        List<String> tooltip = new LinkedList<>();
        if (itemStack != null && slot == 0) {
            if (this.worldGenEntry.isSilkTouchNeeded())
                tooltip.add(Conditional.silkTouch.toString());

            List<String> biomes = this.worldGenEntry.getBiomeRestrictions();
            if (biomes.size() > 0) {
                tooltip.add(TranslationHelper.translateAndFormat("jer.worldgen.biomes") + ":");
                tooltip.addAll(biomes);
            }

            if (Settings.showDevData) {
                tooltip.add(TranslationHelper.translateAndFormat("jer.worldgen.averageChunk") + ":");
                tooltip.add("" + this.worldGenEntry.getAverageBlockCountPerChunk());
            }

        } else {
            tooltip.add(TranslationHelper.translateAndFormat("jer.worldgen.average"));
            String previousChanceString = null;
            for (LootDrop dropItem : this.worldGenEntry.getLootDrops(itemStack)) {
                final String chanceString = dropItem.chanceString();
                if (Objects.equal(chanceString, previousChanceString)) {
                    continue;
                } else {
                    previousChanceString = chanceString;
                }

                String line = "  ";
                if (dropItem.fortuneLevel > 0) {
                    // line += Enchantment.getEnchantmentByLocation("fortune").getTranslatedName(dropItem.fortuneLevel)
                    line += ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation("minecraft.fortune")).getDisplayName(dropItem.fortuneLevel).toString();
                } else {
                    line += TranslationHelper.translateAndFormat("jer.worldgen.base");
                }
                line += ": " + chanceString;
                tooltip.add(line);
            }
        }
        return tooltip.stream().map(StringTextComponent::new).collect(Collectors.toList());
    }

    private List<ITextComponent> getLineTooltip(double mouseX, List<ITextComponent> tooltip) {
        final double exactMouseX = getExactMouseX(mouseX);
        final float[] chances = this.worldGenEntry.getChances();
        final double space = X_AXIS_SIZE / (chances.length * 1D);
        // Calculate the hovered over y value
        final int index = (int) ((exactMouseX - X_OFFSET + 1) / space);
        final int yValue = index + this.worldGenEntry.getMinY();
        if (index >= 0 && index < chances.length) {
            float chance = chances[index] * 100;
            String percent = chance > 0.01f || chance == 0 ? String.format(" (%.2f%%)", chance) : " <0.01%";
            tooltip.add(new StringTextComponent("Y: " + yValue + percent));
        }

        return tooltip;
    }

    private static double getExactMouseX(final double mouseX) {
        Minecraft mc = Minecraft.getInstance();
        final int scaledWidth = mc.getMainWindow().getScaledWidth();
        final double mouseXExact = mc.mouseHelper.getMouseX() * scaledWidth / (double) mc.getMainWindow().getWidth();
        final double mouseXFraction = mouseXExact - Math.floor(mouseXExact);
        return mouseX + mouseXFraction;
    }

    private boolean onGraph(double mouseX, double mouseY) {
        return mouseX >= X_OFFSET - 1
            && mouseX < X_OFFSET + X_AXIS_SIZE
            && mouseY >= Y_OFFSET - Y_AXIS_SIZE - 1
            && mouseY < Y_OFFSET;
    }

}
