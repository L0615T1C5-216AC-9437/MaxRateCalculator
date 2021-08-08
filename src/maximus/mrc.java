package maximus;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.core.World;
import mindustry.game.EventType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.input.Placement;
import mindustry.mod.Mod;
import mindustry.type.*;
import mindustry.ui.Menus;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static mindustry.Vars.player;
import static mindustry.Vars.world;

public class mrc extends Mod {
    private static final KeyCode key = KeyCode.backtick;
    private static final int maxSelection = 500;
    private static final String[][] buttons = menuButtonFormatter("\uF838\t\uF837\t\uF836\t\uF835\n\uF834\t\uF833\t\uF832\t\uF831\n\uF830\t\uF82F\t\uF82E\t\uF82D\n\uF82C\t\uF82B\t\uF82A\t\uF829\nCalculate Max\nCalculate Real");
    private static final Item[] allItems = new Item[] { Items.copper, Items.lead, Items.metaglass, Items.graphite, Items.sand, Items.copper, Items.titanium, Items.thorium, Items.scrap, Items.silicon, Items.plastanium, Items.phaseFabric, Items.surgeAlloy, Items.sporePod, Items.blastCompound, Items.pyratite};


    private static int x1 = -1, y1 = -1, x2 = -1, y2 = -1;
    //calculateMax
    private static final HashMap<Item, List<pcEntry>> itemPC = new HashMap<>();
    private static final HashMap<Liquid, List<pcEntry>> liquidPC = new HashMap<>();
    private static final List<Float> powerPC = new ArrayList<>();

    public mrc(){
        Log.info("Loading Events in Max Rate Calculator.");

        //listen for game load event
        Events.run(Trigger.draw, () -> {
            if (Core.input.keyDown(key) && x1 != -1 && y1 != -1) {
                drawSelection(x1, y1, tileX(Core.input.mouseX()), tileY(Core.input.mouseY()), maxSelection);
            }
        });
        Events.on(ClientLoadEvent.class, event -> {
            Menus.registerMenu(69420, (player, selection) -> {
                if (selection < 0) return;
                if (selection < 16) {
                    calculateMax(x1, y1, x2, y2, allItems[selection]);
                } else {
                    calculation cal = null;
                    switch (selection) {
                        case 16 -> {
                            try {
                                cal = new calculateMax(x1, y1, x2, y2);
                                cal.calculate();
                            } catch (Exception e) {
                                Log.err(e);
                            }
                        }
                        case 17 -> {
                            try {
                                cal = new calculateReal(x1, y1, x2, y2);
                                cal.calculate();
                            } catch (Exception e) {
                                Log.err(e);
                            }
                        }
                    }
                    if (cal != null && !cal.formattedMessage.isEmpty()) {
                        cal.callLabel();
                    } else {
                        Log.err("cal null or format message bad");
                    }
                }
                x1 = -1;
                y1 = -1;
                x2 = -1;
                y2 = -1;
            });
            if (!Core.settings.has("mrcFirstTime")) {
                Menus.infoMessage("""
                        [accent]Thank you [white]for using the Max Rate Calculator

                        To use the Max Rate Calculator, press the [lightgray][` ~][white] key in your keyboard and drag as if you were making a schematic.
                        Anything inside the selection box will be counted in the calculation.

                        If you accidentally use the max rate calculator and a menu pops up, you can press [lightgray][esc][white] to quit menu without triggering anything
                        
                        If you have any questions, you can contact me through [sky]discord[white], L0615T1C5.216AC#9437 or go to [lightgray]http://cn-discord.ddns.net[white] and ping me (Server Owner)
                        
                        [lightgray]This message will not appear again""");
                Core.settings.put("mrcFirstTime", false);
                Core.settings.forceSave();
            }
        });
        Events.run(EventType.Trigger.update, () -> {
            int rawCursorX = World.toTile(Core.input.mouseWorld().x), rawCursorY = World.toTile(Core.input.mouseWorld().y);

            if (Core.input.keyTap(key)) {
                x1 = rawCursorX;
                y1 = rawCursorY;
            }
            if (Core.input.keyRelease(key) && x1 != -1 && y1 != -1) {
                x2 = rawCursorX;
                y2 = rawCursorY;
                Menus.menu(69420, "Max Rate Calculator!", "(Not Coded yet) Select the 1 item being exported and ignore other extra resources\n[accent]Calculate Max[white]: Calculate as if everything was working at 100% all the time.\n[accent]Calculate Real[white]: Wont compensate for missing resources thus lower efficiency accordingly", buttons);
                //calculate(x1, y1, rawCursorX, rawCursorY);
            }
        });
    }

    @Override
    public void loadContent(){
		Log.info("Loading the Max Rate Calculator!");
    }
    //calculation
    private static void calculateMax(int x1, int y1, int x2, int y2, Item out) {
        int xl = Math.min(x1, x2);
        int xr = Math.max(x1, x2);
        int yb = Math.min(y1, y2);
        int yt = Math.max(y1, y2);


    }

    private static class pcEntry {
        public final float production;
        public final float consumption;
        public final boolean optional;

        public pcEntry(float production, float consumption, boolean optional) {
            this.production = production;
            this.consumption = consumption;
            this.optional = optional;
        }
    }

    //menu formatter
    public static String[][] menuButtonFormatter(String input) {
        String[] rows = input.split("\n");
        String[][] out = new String[rows.length][];
        for (int r = 0; r < rows.length; r++) {
            out[r] = rows[r].split("\t");
        }
        return out;
    }
    //anuke
    int tileX(float cursorX) {
        return World.toTile(Core.input.mouseWorld(cursorX, 0).x);
    }

    int tileY(float cursorY) {
        return World.toTile(Core.input.mouseWorld(0, cursorY).y);
    }

    public boolean validBreak(int x, int y){
        return Build.validBreak(player.team(), x, y);
    }
    //anuke likes to draw stuff
    void drawSelection(int x1, int y1, int x2, int y2, int maxLength) {
        //todo: fix weird bloom effect
        Draw.reset();
        Placement.NormalizeDrawResult result = Placement.normalizeDrawArea(Blocks.air, x1, y1, x2, y2, false, maxLength, 1f);
        Placement.NormalizeResult dresult = Placement.normalizeArea(x1, y1, x2, y2, 0, false, maxLength);

        for(int x = dresult.x; x <= dresult.x2; x++){
            for(int y = dresult.y; y <= dresult.y2; y++){
                Tile tile = world.tileBuilding(x, y);
                if(tile == null || !validBreak(tile.x, tile.y)) continue;

                drawBreaking(tile.x, tile.y);
            }
        }

        Lines.stroke(2f);

        Draw.color(Pal.accent);
        Draw.alpha(0.3f);
        float x = (result.x2 + result.x) / 2;
        float y = (result.y2 + result.y) / 2;
        Fill.rect(x, y, result.x2 - result.x, result.y2 - result.y);
    }

    private static void drawBreaking(int x, int y) {
        Tile tile = world.tile(x, y);
        if(tile == null) return;
        Block block = tile.block();

        drawSelected(x, y, block, Pal.accent);
    }

    private static void drawSelected(int x, int y, Block block, Color color) {
        Drawf.selected(x, y, block, color);
    }

    public static class calculation {
        public final int xl;
        public final int xr;
        public final int yb;
        public final int yt;

        public String formattedMessage = "";

        public calculation(int x1, int y1, int x2, int y2) {
            xl = Math.min(x1, x2);
            xr = Math.max(x1, x2);
            yb = Math.min(y1, y2);
            yt = Math.max(y1, y2);
        }

        public void calculate() {

        }

        public void callLabel() {
            Menus.label(formattedMessage, 30, (xl + xr) * 4f, (yb - 5) * 8f);
        }
    }
}
