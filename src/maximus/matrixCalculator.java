package maximus;

import arc.Core;
import arc.graphics.Color;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Prop;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.blocks.units.UnitFactory;
import mindustry.world.consumers.ConsumeItems;
import mindustry.world.consumers.ConsumeLiquid;
import mindustry.world.consumers.ConsumePower;
import mindustry.world.consumers.ConsumeType;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Consumer;

import static maximus.mrc.translatedStringOptional;
import static mindustry.Vars.world;

public class matrixCalculator {
    public static String calculate(int x1, int y1, int x2, int y2, boolean rateLimit) throws Exception {
        return calculate(x1, y1, x2, y2, rateLimit, null);
    }

    public static String calculate(int x1, int y1, int x2, int y2, boolean rateLimit, Object doNotThrottle) throws Exception {
        //setup coordinates
        int xl = Math.min(x1, x2);
        int xr = Math.max(x1, x2);
        int yb = Math.min(y1, y2);
        int yt = Math.max(y1, y2);

        if (xl < 0 || yb < 0 || xr > Vars.world.width() || yt > Vars.world.height())
            throw new Exception("Invalid Coordinates");

        final ArrayList<Building> processed = new ArrayList<>();
        final HashMap<Block, RecipeBuilder> recipes = new HashMap<>();

        for (int x = xl; x <= xr; x++) {
            for (int y = yb; y <= yt; y++) {
                Tile t = world.tile(x, y);
                if (t == null || t.block().isAir() || t.block() instanceof Prop || processed.contains(t.build)) continue; //ignore if null, decoration or already processed
                processed.add(t.build);
                if (!recipes.containsKey(t.block())) {
                    RecipeBuilder rb = new RecipeBuilder();
                    rb.setEmoji(t.block().emoji());
                    //generic
                    if (t.block().consumes.has(ConsumeType.power) && t.block().consumes.get(ConsumeType.power) instanceof ConsumePower cp) {
                        rb.setPowerIn(cp.usage * 60f);
                    }
                    if (t.block().consumes.has(ConsumeType.liquid) && t.block().consumes.get(ConsumeType.liquid) instanceof ConsumeLiquid cl && !(t.block() instanceof Drill)) { //if uses liquid but not a drill
                        rb.addIngredient(cl.liquid, cl.amount * 60f);
                    }
                    //specialized

                    //java 17 switch pattern matching would be perfect here but its would be a pain to make it run on java 8
                    if (t.block() instanceof Drill d && t.build instanceof Drill.DrillBuild db) {
                        if (db.dominantItems > 0) {
                            float boost = db.liquids.total() > 0 || !rateLimit ? d.liquidBoostIntensity * d.liquidBoostIntensity : 1f; //if water cooled or getting max rate
                            if (boost > 1f && t.block().consumes.has(ConsumeType.liquid) && t.block().consumes.get(ConsumeType.liquid) instanceof ConsumeLiquid cl && !(t.block() instanceof Drill)) { //if uses liquid but not a drill
                                rb.addIngredient(cl.liquid, cl.amount * 60f);
                            }
                            float perSecond = db.dominantItems * boost;
                            float difficulty = d.drillTime + d.hardnessDrillMultiplier * db.dominantItem.hardness;
                            rb.addProduct(db.dominantItem, perSecond / difficulty * 60f);
                        }
                    } else if (t.block() instanceof SolidPump sp && t.build instanceof SolidPump.SolidPumpBuild spb) { //water extractor / oil extractor
                        float fraction = Math.max(spb.validTiles + spb.boost + (sp.attribute == null ? 0 : sp.attribute.env()), 0);
                        float maxPump = sp.pumpAmount * fraction;
                        rb.addProduct(sp.result, maxPump * 60f);

                        if (t.block() instanceof Fracker fracker) { //instance off just in case a mod decides to extend this
                            if (fracker.consumes.has(ConsumeType.item) && fracker.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                                for (var a : ci.items) {
                                    rb.addIngredient(a.item, a.amount * 60f / fracker.itemUseTime);
                                }
                            }
                        }
                    } else if (t.block() instanceof Pump pump) {
                        float pumpRate = 0;
                        Liquid liquid = null;
                        if (pump.isMultiblock()) {
                            int tiles = 0;
                            for(Tile other : t.build.tile().getLinkedTilesAs(pump, new Seq<>())) { //t.build.tile to make sure we are on the parent tile (cus getLinked is bad)
                                if(other.floor().liquidDrop == null) continue;
                                if(liquid != null && other.floor().liquidDrop != liquid) {
                                    liquid = null;
                                    tiles = 0;
                                    break;
                                }
                                liquid = other.floor().liquidDrop;
                                tiles++;
                            }
                            if (tiles > 0 && liquid != null) {
                                pumpRate = pump.pumpAmount * tiles;
                            }
                        } else if (t.floor().liquidDrop != null) {
                            liquid = t.floor().liquidDrop;
                            pumpRate = pump.pumpAmount;
                        }

                        if (liquid != null && pumpRate > 0) {
                            rb.addProduct(liquid, pumpRate * 60f);
                        }
                    } else if (t.block() instanceof PowerGenerator pg) {
                        if (t.block() instanceof  ThermalGenerator tg) {
                            Tile tile = t.build.tile; //get origin block
                            rb.setPowerOut(pg.powerProduction * tg.sumAttribute(tg.attribute, tile.x, tile.y) * 60f);
                        } else {
                            rb.setPowerOut(pg.powerProduction * 60f);

                            //damn I wish I could use the java 17 switch
                            if (t.block() instanceof BurnerGenerator bg) {
                                //todo: detect burners
                            /*
                            pc.usesFlammableItem = true;
                            pc.rate = 60f / bg.itemDuration;
                             */
                            } else if (t.block() instanceof DecayGenerator dg) {
                                //todo: detect radioactive reactors
                            /*
                            pc.usesRadioactiveItem = true;
                            pc.rate = 60f / dg.itemDuration;
                             */
                            } else if (t.block() instanceof ItemLiquidGenerator ilg) {
                                if (ilg.consumes.has(ConsumeType.item) && ilg.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                                    for (var a : ci.items) {
                                        rb.addIngredient(a.item, a.amount * 60f / ilg.itemDuration);
                                    }
                                }
                            } else if (t.block() instanceof NuclearReactor nr) {
                                if (nr.consumes.has(ConsumeType.item) && nr.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                                    for (var a : ci.items) {
                                        rb.addIngredient(a.item, a.amount * 60f / nr.itemDuration);
                                    }
                                }
                            } else if (t.block() instanceof ImpactReactor ir) {
                                if (ir.consumes.has(ConsumeType.item) && ir.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                                    for (var a : ci.items) {
                                        rb.addIngredient(a.item, a.amount * 60f / ir.itemDuration);
                                    }
                                }
                            }
                        }

                    } else if (t.block() instanceof GenericCrafter gc) {
                        if (gc.outputItems != null) {
                            for (var a : gc.outputItems) {
                                rb.addProduct(a.item, a.amount * 60f / gc.craftTime);
                            }
                        }
                        if (gc.outputLiquid != null) {
                            float rate = gc.outputLiquid.amount * 60f;
                            if (!(t.block() instanceof LiquidConverter)) { //if crafted
                                rate /= gc.craftTime;
                            }
                            rb.addProduct(gc.outputLiquid.liquid, rate);
                        }
                        if (gc.consumes.has(ConsumeType.item) && gc.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                            for (var a : ci.items) {
                                rb.addIngredient(a.item, a.amount * 60f / gc.craftTime);
                            }
                        }
                    } else if (t.block() instanceof Separator separator) {
                        //calculate average output
                        int totalSlots = 0; //how many slots for random selection
                        HashMap<Item, Integer> chances = new HashMap<>(); //how many slots a item has
                        for (ItemStack is : separator.results) {
                            totalSlots += is.amount;
                            chances.put(is.item, is.amount);
                        }
                        for (Item item : chances.keySet()) {
                            //if chances.get(item) / totalSlots == 1.0, it will make x item every crafting cycle, if 0.5 it'll be every other cycle... so on and so forth
                            rb.addProduct(item, (float) chances.get(item) / totalSlots * 60f / separator.craftTime);
                        }

                        if (separator.consumes.has(ConsumeType.item) && separator.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                            for (var a : ci.items) {
                                rb.addIngredient(a.item, a.amount * 60f / separator.craftTime);
                            }
                        }
                    } else if (t.block() instanceof UnitFactory uf && t.build instanceof UnitFactory.UnitFactoryBuild ufb) {
                        if (ufb.currentPlan != -1) {
                            UnitFactory.UnitPlan up = uf.plans.get(ufb.currentPlan);
                            rb.addProduct(up.unit, 60f / up.time);
                            for (var a : up.requirements) {
                                rb.addIngredient(a.item, a.amount * 60f / up.time);
                            }
                        }
                    } else if (t.block() instanceof Reconstructor r) {
                        /*
                        pc.isReconstructor = true;
                        pc.upgrades = r.upgrades;
                         */
                        if (r.consumes.has(ConsumeType.item) && r.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                            for (var a : ci.items) {
                                rb.addIngredient(a.item, a.amount * 60f / r.constructTime);
                            }
                        }
                    }

                    if (!rb.isEmpty()) recipes.put(t.block(), rb);
                } else {
                    recipes.get(t.block()).addMachine();
                }

            }
        }

        ArrayList<Recipe> finalRecipes = new ArrayList<>();
        HashMap<Recipe, Recipe> lessCapacityClonedRecipes = new HashMap<>();
        for (RecipeBuilder rb : recipes.values()) {
            Recipe r = rb.build();
            boolean add = true;
            if (rateLimit) for (Recipe r2 : finalRecipes) {
                if (r2.clonedRecipe(r)) {
                    if (lessCapacityClonedRecipes.containsKey(r2)) {
                        Recipe r3 = lessCapacityClonedRecipes.get(r2);
                        int c1 = 0, c2 = 0;
                        for (IPRate ipr : r.products) {
                            c1 += ipr.rate * r.multiplier * r.machines;
                        }
                        for (IPRate ipr : r3.products) {
                            c2 += ipr.rate * r.multiplier * r.machines;
                        }
                        if (c1 > c2) {
                            lessCapacityClonedRecipes.put(r, r3);
                            lessCapacityClonedRecipes.put(r2, r);
                        } else {
                            lessCapacityClonedRecipes.put(r3, r);
                        }
                    } else {
                        lessCapacityClonedRecipes.put(r2, r);
                    }
                    add = false;
                }
            }
            if (add) finalRecipes.add(r);
        }
        if (rateLimit) {
            float max;
            float[] perfectRatios;
            while (true) {
                System.out.println("looping");
                //calculate output and inputs
                ArrayList<Object> placeholders = new ArrayList<>();
                HashMap<Object, Float> output = new HashMap<>();
                ArrayList<Object> objectIndex = new ArrayList<>();

                HashMap<Object, ip> allIP = getIP(finalRecipes);
                for (Object o : allIP.keySet()) {
                    objectIndex.add(o);
                    ip ip = allIP.get(o);
                    if (ip.p == 0) {
                        placeholders.add(o);
                    } else if (ip.i == 0) {
                        output.put(o, ip.p);
                    }
                }
                //make matrix
                float[][] a = new float[objectIndex.size()][finalRecipes.size() + placeholders.size() + 1];
                for (int l = 0; l < a.length - 1; l++) {
                    for (int r = 0; r < a[0].length; r++) {
                        a[l][r] = 0f;
                    }
                }
                //placeholder for objects used as input
                for (int i = 0; i < placeholders.size(); i++) {
                    a[objectIndex.indexOf(placeholders.get(i))][finalRecipes.size() + i] = 1;
                }
                //attempt to max out every item not being used but produced
                for (Object o : output.keySet()) {
                    float val = output.get(o);
                    a[objectIndex.indexOf(o)][a[0].length - 1] = val;
                }
                //add each recipe to matrix
                for (Recipe r : finalRecipes) {
                    HashMap<Object, ip> localIP = new HashMap<>();
                    r.getProducts(IPRate -> {
                        localIP.putIfAbsent(IPRate.object, new ip());
                        localIP.get(IPRate.object).incrementP(IPRate.rate);
                    });
                    r.getIngredients(IPRate -> {
                        localIP.putIfAbsent(IPRate.object, new ip());
                        localIP.get(IPRate.object).incrementI(IPRate.rate);
                    });
                    localIP.forEach((k, v) -> a[objectIndex.indexOf(k)][finalRecipes.indexOf(r)] = v.p - v.i);
                }

                //get perfect ratio
                perfectRatios = matrix.calculate(a);
                //get max recipe
                max = 0f;
                int maxObject = -1;
                for (int i = 0; i < finalRecipes.size(); i++) {
                    float f = perfectRatios[i];
                    if (f > max) {
                        max = f;
                        maxObject = i;
                    }
                }
                if (maxObject > -1) {
                    Recipe r = finalRecipes.get(maxObject);
                    if (lessCapacityClonedRecipes.containsKey(r)) {
                        Recipe r2 = lessCapacityClonedRecipes.get(r);
                        lessCapacityClonedRecipes.remove(r);
                        float powerIn = r.powerIn + r2.powerIn;
                        float powerOut = r.powerOut + r2.powerOut;
                        HashMap<Object, Float> ingredients = new HashMap<>();
                        HashMap<Object, Float> products = new HashMap<>();
                        r.getIngredients(ipr -> {
                            ingredients.putIfAbsent(ipr.object, 0f);
                            ingredients.put(ipr.object, ingredients.get(ipr.object) + ipr.rate);
                        });
                        r2.getIngredients(ipr -> {
                            ingredients.putIfAbsent(ipr.object, 0f);
                            ingredients.put(ipr.object, ingredients.get(ipr.object) + ipr.rate);
                        });
                        r.getProducts(ipr -> {
                            products.putIfAbsent(ipr.object, 0f);
                            products.put(ipr.object, products.get(ipr.object) + ipr.rate);
                        });
                        r2.getProducts(ipr -> {
                            products.putIfAbsent(ipr.object, 0f);
                            products.put(ipr.object, products.get(ipr.object) + ipr.rate);
                        });
                        IPRate[] finalIngredients = new IPRate[ingredients.size()];
                        IPRate[] finalProducts = new IPRate[products.size()];
                        int i = 0;
                        for (Object o : ingredients.keySet()) {
                            finalIngredients[i++] = new IPRate(o, ingredients.get(o));
                        }
                        i = 0;
                        for (Object o : products.keySet()) {
                            finalProducts[i++] = new IPRate(o, products.get(o));
                        }
                        Recipe r3 = new Recipe(finalIngredients, finalProducts, powerIn, powerOut, 1, r.emoji);
                        //replace r with r3
                        finalRecipes.add(r3);
                        finalRecipes.remove(r);
                        //if there is a tertiary duplicate recipe
                        if (lessCapacityClonedRecipes.containsKey(r2)) {
                            lessCapacityClonedRecipes.put(r3, lessCapacityClonedRecipes.get(r2));
                            lessCapacityClonedRecipes.remove(r2);
                        }
                    } else break;
                } else break;
            }

            //make max recipe 100%
            float fix = 1 / max;
            for (int i = 0; i < perfectRatios.length; i++) {
                perfectRatios[i] *= fix;
            }
            //change multiplier
            for (int i = 0; i < finalRecipes.size(); i++) {
                finalRecipes.get(i).multiplier = perfectRatios[i];
            }
        }

        HashMap<Object, ip> allIP = getIP(finalRecipes);

        StringBuilder builder = new StringBuilder();
        DecimalFormat df = new DecimalFormat("0.00");
        for (Object o : allIP.keySet()) {
            int machines = 0;
            float totalMultiplier = 0f;
            for (Recipe r : finalRecipes) {
                if (r.containsProduct(o)) {
                    machines += r.machines;
                    totalMultiplier += (r.multiplier * r.machines);
                }
            }
            float finalMultiplier = totalMultiplier / machines;
            ip ip = allIP.get(o);
            String emoji = "";
            String name = "";
            String color = "";
            if (o instanceof Item item) {
                emoji = item.emoji();
                name = item.localizedName;
                color = item.color + "";
            } if (o instanceof Liquid liquid) {
                emoji = liquid.emoji();
                name = liquid.localizedName;
                color = liquid.color + "";
            } if (o instanceof UnitType unit) {
                emoji = unit.emoji();
                name = unit.name;
                color = Color.white + "";
            }

            builder.append("\n[white]");
            if (rateLimit) {
                builder.append("([lightgray]").append(df.format(finalMultiplier * 100f)).append("%[white]) ");
            }
            builder.append(emoji).append("[#").append(color).append("]").append(name).append(" :");
            float difference = ip.p - ip.i;
            if (o instanceof UnitType) difference *= 60f; //to unit/minute
            if (0.001f > difference && difference > -0.001f) difference = 0f;
            if (ip.p > 0 || ip.i > 0) builder.append(difference == 0f ? " [lightgray]" : difference < 0 ? " [scarlet]" : " [lime]+").append(df.format(difference));
            if ((Core.settings.getBool("mrcShowZeroAverageMath", true) || difference != 0f) && ip.p > 0 && ip.i > 0) builder.append(" [white]= [lime]+").append(df.format(ip.p)).append(" [white]+ [scarlet]-").append(df.format(ip.i));
            if (000 > 0) builder.append(" [lightgray](-").append(df.format(000)).append(" ").append(translatedStringOptional).append(")");//change 000 to consumption optional
        }

        return builder.toString();
    }

    public static HashMap<Object, ip> getIP(ArrayList<Recipe> recipes) {
        HashMap<Object, ip> allIP = new HashMap<>();

        for (Recipe recipe : recipes) {
            recipe.getProducts(IPRate -> {
                allIP.putIfAbsent(IPRate.object, new ip());
                allIP.get(IPRate.object).incrementP(IPRate.rate);
            });
            recipe.getIngredients(IPRate -> {
                allIP.putIfAbsent(IPRate.object, new ip());
                allIP.get(IPRate.object).incrementI(IPRate.rate);
            });
        }

        return allIP;
    }

    public static class ip {
        public float i = 0;
        public float p = 0;

        public ip() {}

        public void incrementP(float value) {
            p += value;
        }

        public void incrementI(float value) {
            i += value;
        }
    }

    public static class RecipeBuilder {
        //recipe data
        private final ArrayList<IPRate> ingredients = new ArrayList<>();
        private final ArrayList<IPRate> products = new ArrayList<>();
        private float powerIn = 0;
        private float powerOut = 0;
        private int machines = 1;
        private String emoji = "";

        public RecipeBuilder() {}

        //configure
        public void addIngredient(Object object, float rate) {
            ingredients.add(new IPRate(object, rate));
        }

        public void addProduct(Object object, float rate) {
            products.add(new IPRate(object, rate));
        }

        public void setPowerIn(float powerIn) {
            this.powerIn = powerIn;
        }

        public void setPowerOut(float powerOut) {
            this.powerOut = powerOut;
        }

        public void addMachine() {
            machines++;
        }

        public void setEmoji(String emoji) {
            this.emoji = emoji;
        }

        public Recipe build() {
            return new Recipe(ingredients.toArray(new IPRate[0]), products.toArray(new IPRate[0]), powerIn, powerOut, machines, emoji);
        }

        public boolean isEmpty() {
            return ingredients.isEmpty() && products.isEmpty() && machines == 1;
        }
    }

    public static class Recipe {
        //recipe data
        IPRate[] ingredients;
        IPRate[] products;
        float powerIn;
        float powerOut;
        final int machines;
        //balance
        float multiplier = 1f;
        String emoji;

        public Recipe(IPRate[] ingredients, IPRate[] products, float powerIn, float powerOut, int machines, String emoji) {
            this.ingredients = ingredients;
            this.products = products;
            this.powerIn = powerIn;
            this.powerOut = powerOut;
            this.machines = machines;

            this.emoji = emoji;
        }
        //get if contains
        public boolean containsProduct(Object o) {
            for (IPRate ipr : products) {
                if (ipr.object == o) return true;
            }
            return false;
        }

        public boolean containsIngredient(Object o) {
            for (IPRate ipr : ingredients) {
                if (ipr.object == o) return true;
            }
            return false;
        }
        //getting rates
        public void getIngredients(Consumer<IPRate> out) {
            for (IPRate ipr : ingredients) {
                out.accept(ipr.multiplier(multiplier * machines));
            }
        }

        public void getProducts(Consumer<IPRate> out) {
            for (IPRate ipr : products) {
                out.accept(ipr.multiplier(multiplier * machines));
            }
        }
        //cloned recipes
        public boolean clonedRecipe(Recipe r) {
            if (ingredients.length == r.ingredients.length) {
                for (IPRate ipr : ingredients) {
                    boolean found = false;
                    for (IPRate ipr2 : r.ingredients) {
                        if (ipr.sameObject(ipr2)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) return false;
                }
            } else {
                return false;
            }
            if (products.length == r.products.length) {
                for (IPRate ipr : products) {
                    boolean found = false;
                    for (IPRate ipr2 : r.products) {
                        if (ipr.sameObject(ipr2)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) return false;
                }
            } else {
                return false;
            }
            return true;
        }
    }

    public static class IPRate {
        final Object object;
        final float rate;

        public IPRate(Object object, float rate) {
            this.object = object;
            this.rate = rate;
        }

        public IPRate multiplier(float multiplier) {
            return new IPRate(object, rate * multiplier);
        }

        public boolean sameObject(IPRate ipr) {
            return object == ipr.object;
        }
    }
}
