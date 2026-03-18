package com.complextalents.origin.client;

import com.complextalents.dev.SimpleUIFactory;
import com.complextalents.network.PacketHandler;
import com.complextalents.origin.Origin;
import com.complextalents.origin.OriginRegistry;
import com.complextalents.origin.network.SelectOriginPacket;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class OriginSelectionUI {
    public static final ResourceLocation UI_ID = ResourceLocation.fromNamespaceAndPath("complextalents", "origin_selection");

    private final Player player;
    private final IUIHolder holder;

    public OriginSelectionUI(Player player, IUIHolder holder) {
        this.player = player;
        this.holder = holder;
    }

    public static void init() {
        SimpleUIFactory.register(UI_ID, (player, holder) -> new OriginSelectionUI(player, holder).createUI());
    }

    public ModularUI createUI() {
        WidgetGroup root = new WidgetGroup();
        root.setSize(340, 240);
        root.setBackground(ResourceBorderTexture.BORDERED_BACKGROUND);

        LabelWidget title = new LabelWidget();
        title.setSelfPosition(10, 10);
        title.setText("§lSelect Your Origin");
        root.addWidget(title);

        LabelWidget subtitle = new LabelWidget();
        subtitle.setSelfPosition(10, 25);
        subtitle.setText("§7Choose carefully. This choice is permanent.");
        root.addWidget(subtitle);

        DraggableScrollableWidgetGroup scrollable = new DraggableScrollableWidgetGroup();
        scrollable.setSelfPosition(10, 45);
        scrollable.setSize(320, 185);

        WidgetGroup listContainer = new WidgetGroup();
        
        java.util.Collection<Origin> origins = OriginRegistry.getInstance().getAllOrigins();
        int yOffset = 0;
        
        for (Origin origin : origins) {
            WidgetGroup entry = createOriginEntry(origin);
            entry.setSelfPosition(0, yOffset);
            listContainer.addWidget(entry);
            yOffset += entry.getSize().height + 5;
        }

        listContainer.setSize(300, Math.max(185, yOffset));
        scrollable.addWidget(listContainer);

        root.addWidget(scrollable);

        return new ModularUI(root, holder, player);
    }

    private WidgetGroup createOriginEntry(Origin origin) {
        WidgetGroup widget = new WidgetGroup();
        widget.setBackground(ResourceBorderTexture.BUTTON_COMMON);

        LabelWidget name = new LabelWidget();
        name.setSelfPosition(5, 5);
        name.setText("§e" + origin.getDisplayName().getString());
        widget.addWidget(name);
        
        LabelWidget desc = new LabelWidget();
        desc.setSelfPosition(5, 15);
        String descText = origin.getDescription() != null ? origin.getDescription().getString() : "An ancient origin.";
        if (descText.length() > 50) {
            descText = descText.substring(0, 47) + "...";
        }
        desc.setText("§7" + descText);
        widget.addWidget(desc);

        ButtonWidget selectBtn = new ButtonWidget();
        selectBtn.setSelfPosition(240, 5);
        selectBtn.setSize(55, 20);
        selectBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("Select"));
        selectBtn.setOnPressCallback(clickData -> {
            PacketHandler.sendToServer(new SelectOriginPacket(origin.getId()));
            player.closeContainer();
        });
        
        if (origin.getDescription() != null) {
            selectBtn.setHoverTooltips(origin.getDescription().getString());
        }

        widget.addWidget(selectBtn);

        int currentY = 30;
        java.util.List<Origin.OriginSkillDisplay> skills = origin.getDisplaySkills();
        
        if (!skills.isEmpty()) {
            LabelWidget skillsTitle = new LabelWidget();
            skillsTitle.setSelfPosition(5, currentY);
            skillsTitle.setText("§nOrigin Kit:");
            widget.addWidget(skillsTitle);
            currentY += 15;

            for (Origin.OriginSkillDisplay skill : skills) {
                LabelWidget sName = new LabelWidget();
                sName.setSelfPosition(5, currentY);
                String prefix = skill.isActive() ? "§c[A] §r" : "§9[P] §r";
                sName.setText(prefix + "§l" + skill.name());
                widget.addWidget(sName);
                currentY += 10;
                
                LabelWidget sDesc = new LabelWidget();
                sDesc.setSelfPosition(15, currentY);
                String skillDesc = skill.description() != null ? skill.description() : "";
                if (skillDesc.length() > 50) {
                    skillDesc = skillDesc.substring(0, 47) + "...";
                }
                sDesc.setText("§7" + skillDesc);
                sDesc.setHoverTooltips(skill.description());
                widget.addWidget(sDesc);
                currentY += 15;
            }
        }
        
        widget.setSize(300, Math.max(40, currentY + 5));
        return widget;
    }
}
