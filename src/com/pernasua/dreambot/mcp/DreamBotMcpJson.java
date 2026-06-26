package com.pernasua.dreambot.mcp;

import static com.pernasua.dreambot.mcp.RuntimeJson.field;
import static com.pernasua.dreambot.mcp.RuntimeJson.quote;

import org.dreambot.api.methods.magic.Magic;
import org.dreambot.api.methods.magic.Spell;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.wrappers.interactive.Entity;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.Player;
import org.dreambot.api.wrappers.items.GroundItem;
import org.dreambot.api.wrappers.items.Item;
import org.dreambot.api.wrappers.widgets.WidgetChild;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

final class DreamBotMcpJson {
    private final int maxEntities;

    DreamBotMcpJson(int maxEntities) {
        this.maxEntities = maxEntities;
    }

    private <T extends Entity> List<T> byRelevance(List<T> entities) {
        List<Ranked<T>> ranked = new ArrayList<>();
        for (T entity : safe(entities)) {
            if (entity == null || !entity.exists()) {
                continue;
            }
            double distance;
            try {
                distance = entity.distance();
            } catch (Throwable ignored) {
                distance = Double.MAX_VALUE;
            }
            int onScreen;
            try {
                onScreen = entity.isOnScreen() ? 0 : 1;
            } catch (Throwable ignored) {
                onScreen = 1;
            }
            ranked.add(new Ranked<>(entity, onScreen, distance));
        }
        ranked.sort((a, b) -> {
            if (a.onScreen != b.onScreen) {
                return Integer.compare(a.onScreen, b.onScreen);
            }
            return Double.compare(a.distance, b.distance);
        });
        List<T> out = new ArrayList<>();
        for (Ranked<T> item : ranked) {
            out.add(item.entity);
        }
        return out;
    }

    String playersJson(List<Player> players) {
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (Player player : byRelevance(players)) {
            if (player == null || !player.exists()) {
                continue;
            }
            if (count > 0) {
                sb.append(",");
            }
            sb.append(playerJson(player));
            count++;
            if (count >= maxEntities) {
                break;
            }
        }
        sb.append("]");
        return sb.toString();
    }

    String playerJson(Player player) {
        if (player == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("{");
        field(sb, "name", player.getName()).append(",");
        field(sb, "index", player.getIndex()).append(",");
        field(sb, "level", player.getLevel()).append(",");
        field(sb, "health_percent", player.getHealthPercent()).append(",");
        field(sb, "animation", player.getAnimation()).append(",");
        field(sb, "moving", player.isMoving()).append(",");
        field(sb, "in_combat", player.isInCombat()).append(",");
        field(sb, "skulled", player.isSkulled()).append(",");
        appendEntityFields(sb, player).append(",");
        sb.append("\"actions\":").append(stringArrayJson(player.getActions()));
        sb.append("}");
        return sb.toString();
    }

    String npcsJson(List<NPC> npcs) {
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (NPC npc : byRelevance(npcs)) {
            if (npc == null || !npc.exists()) {
                continue;
            }
            if (count > 0) {
                sb.append(",");
            }
            sb.append(npcJson(npc));
            count++;
            if (count >= maxEntities) {
                break;
            }
        }
        sb.append("]");
        return sb.toString();
    }

    String objectsJson(List<GameObject> objects) {
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (GameObject object : byRelevance(objects)) {
            if (object == null || !object.exists()) {
                continue;
            }
            if (count > 0) {
                sb.append(",");
            }
            sb.append(objectJson(object));
            count++;
            if (count >= maxEntities) {
                break;
            }
        }
        sb.append("]");
        return sb.toString();
    }

    String groundItemsJson(List<GroundItem> items) {
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (GroundItem item : byRelevance(items)) {
            if (item == null || !item.exists()) {
                continue;
            }
            if (count > 0) {
                sb.append(",");
            }
            sb.append(groundItemJson(item));
            count++;
            if (count >= maxEntities) {
                break;
            }
        }
        sb.append("]");
        return sb.toString();
    }

    String npcJson(NPC npc) {
        StringBuilder sb = new StringBuilder("{");
        field(sb, "name", npc.getName()).append(",");
        field(sb, "id", npc.getId()).append(",");
        field(sb, "real_id", npc.getRealId()).append(",");
        field(sb, "index", npc.getIndex()).append(",");
        field(sb, "level", npc.getLevel()).append(",");
        field(sb, "health_percent", npc.getHealthPercent()).append(",");
        field(sb, "animation", npc.getAnimation()).append(",");
        field(sb, "moving", npc.isMoving()).append(",");
        field(sb, "in_combat", npc.isInCombat()).append(",");
        appendEntityFields(sb, npc).append(",");
        sb.append("\"actions\":").append(stringArrayJson(npc.getActions()));
        sb.append("}");
        return sb.toString();
    }

    String objectJson(GameObject object) {
        StringBuilder sb = new StringBuilder("{");
        field(sb, "name", object.getName()).append(",");
        field(sb, "id", object.getId()).append(",");
        field(sb, "real_id", object.getRealId()).append(",");
        field(sb, "index", object.getIndex()).append(",");
        appendEntityFields(sb, object).append(",");
        sb.append("\"actions\":").append(stringArrayJson(object.getActions()));
        sb.append("}");
        return sb.toString();
    }

    String groundItemJson(GroundItem item) {
        StringBuilder sb = new StringBuilder("{");
        field(sb, "name", item.getName()).append(",");
        field(sb, "id", item.getId()).append(",");
        field(sb, "amount", item.getAmount()).append(",");
        field(sb, "ownership", item.getOwnership()).append(",");
        field(sb, "despawn_time", item.getDespawnTime()).append(",");
        field(sb, "visible_time", item.getVisibleTime()).append(",");
        appendEntityFields(sb, item).append(",");
        sb.append("\"actions\":").append(stringArrayJson(item.getActions()));
        sb.append("}");
        return sb.toString();
    }

    private StringBuilder appendEntityFields(StringBuilder sb, Entity entity) {
        field(sb, "distance", entity.distance()).append(",");
        field(sb, "on_screen", entity.isOnScreen()).append(",");
        sb.append("\"tile\":").append(tileJson(entity.getTile())).append(",");
        sb.append("\"clickable_point\":").append(pointJson(entity.getClickablePoint())).append(",");
        sb.append("\"center_point\":").append(pointJson(entity.getCenterPoint())).append(",");
        sb.append("\"bounds\":").append(rectangleJson(safeRectangle(entity)));
        return sb;
    }

    private Rectangle safeRectangle(Entity entity) {
        try {
            return entity.getBoundingBox();
        } catch (Throwable ignored) {
            return null;
        }
    }

    String itemJson(Item item) {
        if (item == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("{");
        field(sb, "name", item.getName()).append(",");
        field(sb, "id", item.getId()).append(",");
        field(sb, "amount", item.getAmount()).append(",");
        field(sb, "slot", item.getSlot()).append(",");
        field(sb, "stackable", item.isStackable()).append(",");
        field(sb, "noted", item.isNoted()).append(",");
        sb.append("\"actions\":").append(stringArrayJson(item.getActions()));
        sb.append("}");
        return sb.toString();
    }

    String widgetJson(WidgetChild widget) {
        if (widget == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("{");
        field(sb, "widget_id", widget.getWidgetId()).append(",");
        field(sb, "id", widget.getID()).append(",");
        field(sb, "raw_id", widget.getRawId()).append(",");
        field(sb, "real_id", widget.getRealID()).append(",");
        field(sb, "parent_id", widget.getParentID()).append(",");
        field(sb, "child_id", widget.getChildId()).append(",");
        field(sb, "grandchild_id", widget.getGrandChildId()).append(",");
        field(sb, "index", widget.getIndex()).append(",");
        field(sb, "type", widget.getType()).append(",");
        field(sb, "text", widget.getText()).append(",");
        field(sb, "name", widget.getName()).append(",");
        field(sb, "tooltip", widget.getTooltip()).append(",");
        field(sb, "selected_action", widget.getSelectedAction()).append(",");
        field(sb, "spell", widget.getSpell()).append(",");
        field(sb, "item_id", widget.getItemId()).append(",");
        field(sb, "item_stack", widget.getItemStack()).append(",");
        field(sb, "x", widget.getX()).append(",");
        field(sb, "y", widget.getY()).append(",");
        field(sb, "relative_x", widget.getRelativeX()).append(",");
        field(sb, "relative_y", widget.getRelativeY()).append(",");
        field(sb, "width", widget.getWidth()).append(",");
        field(sb, "height", widget.getHeight()).append(",");
        field(sb, "visible", widget.isVisible()).append(",");
        field(sb, "hidden", widget.isHidden()).append(",");
        sb.append("\"bounds\":").append(rectangleJson(widget.getRectangle())).append(",");
        sb.append("\"actions\":").append(stringArrayJson(widget.getActions()));
        sb.append("}");
        return sb.toString();
    }

    String tileJson(Tile tile) {
        if (tile == null) {
            return "null";
        }
        return "{\"x\":" + tile.getX() + ",\"y\":" + tile.getY() + ",\"z\":" + tile.getZ() + "}";
    }

    String pointJson(Point point) {
        if (point == null) {
            return "null";
        }
        return "{\"x\":" + point.x + ",\"y\":" + point.y + "}";
    }

    String rectangleJson(Rectangle rectangle) {
        if (rectangle == null) {
            return "null";
        }
        return "{\"x\":" + rectangle.x + ",\"y\":" + rectangle.y + ",\"width\":" + rectangle.width + ",\"height\":" + rectangle.height + "}";
    }

    String intArrayJson(int[] values) {
        StringBuilder sb = new StringBuilder("[");
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(values[i]);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    String stringArrayJson(String[] values) {
        StringBuilder sb = new StringBuilder("[");
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(quote(values[i]));
            }
        }
        sb.append("]");
        return sb.toString();
    }

    String stringListJson(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String value : safe(values)) {
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(quote(value));
        }
        sb.append("]");
        return sb.toString();
    }

    <T> List<T> safe(List<T> values) {
        return values == null ? new ArrayList<>() : values;
    }

    String spellJson(Spell spell) {
        if (spell == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("{");
        field(sb, "name", spell.toString()).append(",");
        field(sb, "level", spell.getLevel()).append(",");
        field(sb, "parent", spell.getParent()).append(",");
        field(sb, "child", spell.getChild()).append(",");
        field(sb, "can_cast", Magic.canCast(spell));
        sb.append("}");
        return sb.toString();
    }

    private static final class Ranked<T> {
        final T entity;
        final int onScreen;
        final double distance;

        Ranked(T entity, int onScreen, double distance) {
            this.entity = entity;
            this.onScreen = onScreen;
            this.distance = distance;
        }
    }

}
