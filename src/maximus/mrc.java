package maximus;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.Dialog;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.game.EventType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.input.Placement;
import mindustry.mod.Mod;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.ui.Menus;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;

import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;

import static arc.Core.settings;
import static mindustry.Vars.player;
import static mindustry.Vars.world;

public class mrc extends Mod {
    private static KeyCode key = KeyCode.backtick;
    private static final int maxSelection = 500;
    public static ResourceBundle bundle;
    //menu
    public static String menuTitle = "";
    public static String menuDescription = "";
    private static int menuID;
    //translations
    public static String translatedStringPower = "";
    public static String translatedStringOptional = "";
    public static String translatedStringMaxTitle = "";
    public static String translatedStringRealTitle = "";
    public static String translatedStringPowerGeneration = "";

    private static int x1 = -1, y1 = -1, x2 = -1, y2 = -1;

    public mrc(){
        Log.info("Loading Events in Max Rate Calculator.");

        //listen for game load event
        Events.run(Trigger.draw, () -> {
            if (Vars.state.isPlaying() && Core.input.keyDown(key) && x1 != -1 && y1 != -1) {
                drawSelection(x1, y1, tileX(Core.input.mouseX()), tileY(Core.input.mouseY()));
            }
        });
        Events.on(ClientLoadEvent.class, event -> {
            if (Vars.mobile) {
                Vars.ui.showInfo("This mod is not made for mobile!");
            }
            //load language pack
            Locale locale;
            String loc = settings.getString("locale");
            if(loc.equals("default")){
                locale = Locale.getDefault();
            }else{
                if(loc.contains("_")){
                    String[] split = loc.split("_");
                    locale = new Locale(split[0], split[1]);
                }else{
                    locale = new Locale(loc);
                }
            }
            try {
                bundle = ResourceBundle.getBundle("languagePack", locale);
            } catch (Exception ignored) {
                Log.err("No language pack available for Max Rate Calculator, defaulting to english");
                bundle = ResourceBundle.getBundle("languagePack", Locale.ENGLISH);
            }
            //setup
            menuTitle = bundle.getString("mrc.mrc");
            menuDescription = bundle.getString("mrc.menuDescription");


            translatedStringPower = Core.bundle.get("bar.power");
            translatedStringOptional = mrc.bundle.getString("optional");

            translatedStringRealTitle = mrc.bundle.getString("calculateReal") + mrc.bundle.getString("calculateReal.label");
            translatedStringPowerGeneration = mrc.bundle.getString("powerGeneration");
            translatedStringMaxTitle = mrc.bundle.getString("calculateMaximum") + "\n[orange]=========================[white]";

            for (KeyCode kc : KeyCode.all) { //this is probably a terrible implementation of custom key binds
                if (kc.value.equalsIgnoreCase(settings.getString("mrcKey", "`"))) {
                    key = kc;
                    break;
                }
            }

            //add setting to core bundle
            var coreBundle = Core.bundle.getProperties();
            coreBundle.put("setting.mrcSendInfoMessage.name", mrc.bundle.getString("mrc.settings.SendInfoMessage"));
            coreBundle.put("setting.mrcShowZeroAverageMath.name", mrc.bundle.getString("mrc.settings.ShowZeroAverageMath"));
            Core.bundle.setProperties(coreBundle);
            //add custom settings
            settings.put("uiscalechanged", false);//stop annoying "ui scale changed" message
            addBooleanGameSetting("mrcSendInfoMessage", false);
            addBooleanGameSetting("mrcShowZeroAverageMath", true);

            //register menu
            menuID = Menus.registerMenu((player, selection) -> {
                System.out.println(Vars.state.isPlaying());
                if (selection < 0 || !Vars.state.isPlaying()) {
                    x1 = -1;
                    y1 = -1;
                    x2 = -1;
                    y2 = -1;
                    return;
                }
                int itemListSize = Vars.content.items().size;
                System.out.println(selection);
                System.out.println(itemListSize);
                System.out.println(Vars.content.liquids().size);
                if (selection < itemListSize + Vars.content.liquids().size) {
                    try {
                        Object o;
                        if (selection < itemListSize) {
                            o = Vars.content.items().get(selection);
                        } else {
                            o = Vars.content.liquids().get(selection - itemListSize);
                        }
                        //calculate
                        String text = translatedStringRealTitle + matrixCalculator.calculate(x1, y1, x2, y2, true, o);
                        if (settings.getBool("mrcSendInfoMessage", false)) {
                            Vars.ui.showInfo(text);
                        } else {
                            Menus.label(text, 30, (x1 + ((x2 - x1) / 2f)) * 8f, (Math.min(y1, y2) - 5) * 8f);
                        }
                    } catch (Exception e) {
                        Log.err(e);
                    } finally {
                        x1 = -1;
                        y1 = -1;
                        x2 = -1;
                        y2 = -1;
                    }
                } else {
                    int actionSType = selection - itemListSize - Vars.content.liquids().size;
                    try {
                        switch (actionSType) {
                            case 0 -> {
                                settings.put("mrcUseMatrixCalculator", !settings.getBool("mrcUseMatrixCalculator", true));
                                Menus.menu(menuID, menuTitle, menuDescription, getButtons());
                            }
                            case 1, 2 -> {
                                try {
                                    //calculate
                                    boolean rateLimit = actionSType == 2;
                                    String data;
                                    if (settings.getBool("mrcUseMatrixCalculator", true)) {
                                        data = matrixCalculator.calculate(x1, y1, x2, y2, rateLimit);
                                    } else {
                                        data = new legacyCalculator(x1, y1, x2, y2, rateLimit).formattedMessage;
                                    }
                                    String text = (rateLimit ? translatedStringRealTitle : translatedStringMaxTitle) + data;
                                    if (settings.getBool("mrcSendInfoMessage", false)) {
                                        Vars.ui.showInfo(text);
                                    } else {
                                        Menus.label(text, 30, (x1 + ((x2 - x1) / 2f)) * 8f, (Math.min(y1, y2) - 5) * 8f);
                                    }
                                } catch (Exception e) {
                                    Log.err(e);
                                } finally {
                                    x1 = -1;
                                    y1 = -1;
                                    x2 = -1;
                                    y2 = -1;
                                }
                            }
                            case 3 -> {
                                //info
                            }
                            case 4 -> Vars.ui.showConfirm("Do you want to rebind Max Rate Calculator?", () -> {
                                Dialog dialog = new Dialog(Core.bundle.get("keybind.press", "Press a key..."));
                                dialog.titleTable.getCells().first().pad(4);
                                dialog.addListener(new InputListener(){
                                    @Override
                                    public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode keycode){
                                        if(keycode == KeyCode.escape) return false;
                                        key = keycode;
                                        settings.put("mrcKey", key.value);
                                        Vars.ui.showInfo("Max Rate Calculator Key set to " + key.value);
                                        dialog.hide();
                                        return false;
                                    }

                                    @Override
                                    public boolean keyDown(InputEvent event, KeyCode keycode){
                                        if(keycode == KeyCode.escape) return false;
                                        key = keycode;
                                        settings.put("mrcKey", key.value);
                                        Vars.ui.showInfo("Max Rate Calculator Key set to " + key.value);
                                        dialog.hide();
                                        return false;
                                    }
                                });
                                dialog.show();
                            });
                        }
                    } catch (Exception e) {
                        Log.err(e);
                    }
                }
            });
            if (!settings.has("mrcFirstTime")) {
                Menus.infoMessage(bundle.getString("mrc.firstTimeMessage"));
                settings.put("mrcFirstTime", false);
                settings.forceSave();
            }
        });

        Events.run(EventType.Trigger.update, () -> {
            if (Vars.state.isPlaying() && !Vars.ui.chatfrag.shown() && !Core.scene.hasDialog()) {
                int rawCursorX = World.toTile(Core.input.mouseWorld().x), rawCursorY = World.toTile(Core.input.mouseWorld().y);

                if (Core.input.keyTap(key)) {
                    x1 = rawCursorX;
                    y1 = rawCursorY;
                }
                if (Core.input.keyRelease(key) && x1 != -1 && y1 != -1) {
                    x2 = rawCursorX;
                    y2 = rawCursorY;
                    Menus.menu(menuID, menuTitle, menuDescription, getButtons());
                    //calculate(x1, y1, rawCursorX, rawCursorY);
                }
            }
        });
    }

    //menu formatter
    public static String[][] getButtons() {
        return dynamicButtonFormatter(settings.getBool("mrcUseMatrixCalculator", true) ? "Using Matrix Calculator" : "Using Legacy Calculator", bundle.getString("calculateMaximum"), bundle.getString("calculateReal"), bundle.getString("info"), bundle.getString("changeKeyBind"));
    }

    public static String[][] dynamicButtonFormatter(String... buttons) {
        ArrayList<ArrayList<String>> list = new ArrayList<>();
        int buttonNumber = 0;
        for (Item i : Vars.content.items()) {
            if (buttonNumber++ % 4 == 0) {
                list.add(new ArrayList<>());
            }
            list.get(list.size() - 1).add(i.emoji());
        }
        for (Liquid l : Vars.content.liquids()) {
            if (buttonNumber++ % 4 == 0) {
                list.add(new ArrayList<>());
            }
            list.get(list.size() - 1).add(l.emoji());
        }
        for (String s : buttons) {
            list.add(new ArrayList<>());
            list.get(list.size() - 1).add(s);
        }
        //to 2d string array
        String[][] out = new String[list.size()][];
        for (int i = 0; i < list.size(); i++) {
            ArrayList<String> al = list.get(i);
            out[i] = new String[al.size()];
            for (int r = 0; r < al.size(); r++) {
                out[i][r] = al.get(r);
            }
        }
        return out;
    }
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
    void drawSelection(int x1, int y1, int x2, int y2) {
        //todo: fix weird bloom effect
        Draw.reset();
        Placement.NormalizeDrawResult result = Placement.normalizeDrawArea(Blocks.air, x1, y1, x2, y2, false, mrc.maxSelection, 1f);
        Placement.NormalizeResult dresult = Placement.normalizeArea(x1, y1, x2, y2, 0, false, mrc.maxSelection);

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

    public static void addBooleanGameSetting(String key, boolean defaultBooleanValue){
        Vars.ui.settings.game.checkPref(key, settings.getBool(key, defaultBooleanValue));
    }
}
