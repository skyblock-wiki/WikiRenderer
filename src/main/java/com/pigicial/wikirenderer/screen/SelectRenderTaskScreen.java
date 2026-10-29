package com.pigicial.wikirenderer.screen;

import com.pigicial.wikirenderer.render.batch.BatchPropertyBundle;
import com.pigicial.wikirenderer.render.batch.ItemBatchRenderTask;
import com.pigicial.wikirenderer.screen.owo.base.BaseOwoScreen;
import com.pigicial.wikirenderer.screen.owo.container.FlowLayout;
import com.pigicial.wikirenderer.screen.owo.container.UIContainers;
import com.pigicial.wikirenderer.screen.owo.core.*;
import com.pigicial.wikirenderer.util.Translate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class SelectRenderTaskScreen extends BaseOwoScreen<FlowLayout> {

    private final Collection<ItemStack> items;
    private final Collection<ItemStack> playerHeadItems;
    private boolean lastHeadItemsState;

    public SelectRenderTaskScreen(Collection<ItemStack> items) {
        this.items = items;
        this.playerHeadItems = items.stream().filter(item -> item.is(Items.PLAYER_HEAD)).toList();
        this.lastHeadItemsState = this.isUsingOnlyHeadItems();
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::horizontalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent
                .surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);

        FlowLayout mainPanel = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
        mainPanel.surface(Surface.PANEL).padding(Insets.of(5)).horizontalAlignment(HorizontalAlignment.CENTER);

        mainPanel.child(WikiRendererUI.label(Translate.gui("select_batch_operation")).shadow(true).margins(Insets.of(5).withBottom(10)));

        FlowLayout contentPanel = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        contentPanel.verticalAlignment(VerticalAlignment.CENTER);

        contentPanel.child(UIContainers.verticalFlow(Sizing.content(), Sizing.content())
                .child(WikiRendererUI.button(Translate.gui("select_item_batch"), _ -> {
                    ItemBatchRenderTask.BATCH_ITEM.action.accept("inventory", this.getUsedItems());
                    this.onClose();
                }).horizontalSizing(Sizing.fixed(80)).margins(Insets.bottom(5)))
                .child(WikiRendererUI.button(Translate.gui("select_block_batch"), _ -> {
                    ItemBatchRenderTask.BATCH_BLOCK.action.accept("inventory", this.getUsedItems());
                    this.onClose();
                }).horizontalSizing(Sizing.fixed(80)).margins(Insets.bottom(5)))
                .child(WikiRendererUI.button(Translate.gui("select_tooltip_batch"), _ -> {
                    ItemBatchRenderTask.BATCH_TOOLTIP.action.accept("inventory", this.getUsedItems());
                    this.onClose();
                }).horizontalSizing(Sizing.fixed(80)).margins(Insets.bottom(5)))
                .child(WikiRendererUI.button(Translate.gui("select_atlas"), _ -> {
                    ItemBatchRenderTask.ITEM_ATLAS.action.accept("inventory", this.getUsedItems());
                    this.onClose();
                }).horizontalSizing(Sizing.fixed(80)))
                .padding(Insets.of(5))
        );

        FlowLayout itemPreviewPanel = UIContainers.verticalFlow(Sizing.content(), Sizing.content());

        itemPreviewPanel.child(WikiRendererUI.label(Translate.gui("render_task_size", this.getUsedItems().size())).margins(Insets.of(7)))
                .horizontalAlignment(HorizontalAlignment.CENTER).padding(Insets.of(3))
                .surface(Surface.flat(0x77000000).and(Surface.outline(0x77000000)))
                .margins(Insets.left(10));

        FlowLayout itemContainer = UIContainers.verticalFlow(Sizing.content(), Sizing.content());

        List<ItemStack> itemList = this.getUsedItems().stream().toList();
        int rows = Mth.positiveCeilDiv(itemList.size(), 9);
        for (int row = 0; row < rows; row++) {
            FlowLayout rowContainer = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());

            for (int column = 0; column < 9; column++) {
                int index = row * 9 + column;
                if (index >= itemList.size()) break;

                rowContainer.child(WikiRendererUI.item(itemList.get(index)));
            }

            itemContainer.child(rowContainer);
        }

        itemPreviewPanel.child(UIContainers.verticalScroll(Sizing.content(), Sizing.fixed(Math.min(250, rows * 16)), itemContainer));

        contentPanel.child(itemPreviewPanel);
        mainPanel.child(contentPanel);

        if (!playerHeadItems.isEmpty()) {
            WikiRendererUI.booleanControl(mainPanel, BatchPropertyBundle.ONLY_PLAYER_HEADS, "only_render_player_heads");
        }

        rootComponent.child(mainPanel);
    }

    private boolean isUsingOnlyHeadItems() {
        return !playerHeadItems.isEmpty() && BatchPropertyBundle.ONLY_PLAYER_HEADS.get();
    }

    private Collection<ItemStack> getUsedItems() {
        return this.isUsingOnlyHeadItems() ? playerHeadItems : items;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics) {
        super.extractRenderState(graphics);
        if (lastHeadItemsState != this.isUsingOnlyHeadItems()) {
            lastHeadItemsState = !lastHeadItemsState;
            this.uiAdapter = null;
            this.rebuildWidgets();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
