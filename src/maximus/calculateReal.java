package maximus;

import arc.struct.Seq;
import arc.util.Log;
import arc.util.Nullable;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.MendProjector;
import mindustry.world.blocks.defense.OverdriveProjector;
import mindustry.world.blocks.power.ImpactReactor;
import mindustry.world.blocks.power.ItemLiquidGenerator;
import mindustry.world.blocks.power.NuclearReactor;
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.blocks.production.*;
import mindustry.world.consumers.ConsumeItems;
import mindustry.world.consumers.ConsumeLiquid;
import mindustry.world.consumers.ConsumePower;
import mindustry.world.consumers.ConsumeType;

import java.text.DecimalFormat;
import java.util.*;

import static mindustry.Vars.world;

public class calculateReal extends mrc.calculation {
    private final ArrayList<pc> apc = new ArrayList<>();
    private final ArrayList<Object> intermediate = new ArrayList<>();
    //
    private static final String label = "Real Ratios\n[lightgray]%[gray]: [lightgray]% of total capacity in use\n[orange]=========================[white]";
    //old
    public final HashMap<Item, List<pcEntry>> itemPC ;

    public calculateReal(int x1, int y1, int x2, int y2) throws Exception {
        super(x1, y1, x2, y2);

        itemPC = new HashMap<>();

        if (xl < 0 || yb < 0 || xr > Vars.world.width() || yt > Vars.world.height()) throw new Exception("Invalid Coordinates");
    }

    @Override
    public void calculate() {
        ArrayList<Building> repeat = new ArrayList<>();
        for (int x = xl; x <= xr; x++) {
            for (int y = yb; y <= yt; y++) {
                Tile t = world.tile(x, y);
                if (t == null) continue;
                Category category = t.block().category;
                if (category == Category.distribution) {
                    continue;
                }
                if (repeat.contains(t.build)) continue;
                pc pc = new pc();
                //generic
                if (t.block().consumes.has(ConsumeType.power) && t.block().consumes.get(ConsumeType.power) instanceof ConsumePower cp) {
                    pc.powerConsumption = cp.usage * 60f;
                }
                if (t.block().consumes.has(ConsumeType.liquid) && t.block().consumes.get(ConsumeType.liquid) instanceof ConsumeLiquid cl) {
                    pc.liquidUsage = cl.liquid;
                    pc.liquidUsageRate = cl.amount * 60f;
                }
                //drills
                if (t.block() instanceof Drill d && t.build instanceof Drill.DrillBuild db) {
                    if (db.dominantItems > 0) {
                        float boost = db.liquids.total() > 0 ? d.liquidBoostIntensity * d.liquidBoostIntensity : 1f;
                        if (boost == 1f) {
                            pc.liquidUsage = null;
                            pc.liquidProductionRate = 0;
                        }
                        float perSecond = db.dominantItems * boost;
                        float difficulty = d.drillTime + d.hardnessDrillMultiplier * db.dominantItem.hardness;
                        float rate = perSecond / difficulty;

                        pc.products = new items[] { new items(db.dominantItem, rate) };
                        pc.rate = 60f;
                    }
                }
                if (t.block() instanceof Fracker fracker) {
                    if (fracker.consumes.has(ConsumeType.item) && fracker.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        pc.materials = IStoItems(ci.items);
                        pc.rate = 60f / fracker.itemUseTime;

                    }
                }
                //liquids
                if (t.block() instanceof SolidPump sp && t.build instanceof SolidPump.SolidPumpBuild spb) {
                    float fraction = Math.max(spb.validTiles + spb.boost + (sp.attribute == null ? 0 : sp.attribute.env()), 0);
                    float maxPump = sp.pumpAmount * fraction;
                    pc.liquidProduct = sp.result;
                    pc.liquidProductionRate = maxPump * 60f;
                } else if (t.block() instanceof Pump pump) {
                    float pumpRate = 0;
                    Liquid liquid = null;
                    if (pump.isMultiblock()) {
                        int tiles = 0;
                        for(Tile other : t.build.tile().getLinkedTilesAs(pump, new Seq<>())) { //t.build.tile to make sure we are on the parent tile (cus getLinked is bad)
                            if(other.floor().liquidDrop == null) continue;
                            if(other.floor().liquidDrop != liquid && liquid != null) {
                                liquid = null;
                                tiles = -1;
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
                        pc.liquidProduct = liquid;
                        pc.liquidProductionRate = pumpRate * 60f;
                    }
                }
                //power
                if (t.block() instanceof ItemLiquidGenerator ilg) {
                    if (ilg.consumes.has(ConsumeType.item) && ilg.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        pc.materials = IStoItems(ci.items);
                        pc.rate = 60f/ ilg.itemDuration;
                    }
                } else if (t.block() instanceof NuclearReactor nr) {
                    if (nr.consumes.has(ConsumeType.item) && nr.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        pc.materials = IStoItems(ci.items);
                        pc.rate = 60f/ nr.itemDuration;
                    }
                } else if (t.block() instanceof ImpactReactor ir) {
                    if (ir.consumes.has(ConsumeType.item) && ir.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        pc.materials = IStoItems(ci.items);
                        pc.rate = 60f/ ir.itemDuration;
                    }
                }
                if (t.block() instanceof PowerGenerator pg) {
                    pc.powerProduction = pg.powerProduction * 60f;
                }
                //crafting
                if (t.block() instanceof GenericCrafter gc) {
                    if (gc.outputItems != null) {
                        pc.rate = 60f / gc.craftTime;
                        pc.products = IStoItems(gc.outputItems);
                    }
                    if (gc.outputLiquid != null) {
                        pc.liquidProduct = gc.outputLiquid.liquid;
                        if (t.block() instanceof LiquidConverter) { //makes liquid per tick
                            pc.liquidProductionRate = gc.outputLiquid.amount * 60f;
                        } else { //per craft
                            pc.liquidProductionRate = gc.outputLiquid.amount / (60f / gc.craftTime);
                        }
                    }
                    if (gc.consumes.has(ConsumeType.item) && gc.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        pc.rate = 60f / gc.craftTime;
                        pc.materials = IStoItems(ci.items);
                    }
                }
                if (t.block() instanceof Separator separator) {
                    int totalSlots = 0; //how many slots for random selection
                    HashMap<Item, Integer> chances = new HashMap<>(); //how many slots a item has
                    for (ItemStack is : separator.results) {
                        totalSlots += is.amount;
                        chances.put(is.item, is.amount);
                    }
                    items[] items = new items[chances.size()];
                    int i = 0;
                    for (Item item : chances.keySet()) {
                        float selectionChance = (float) chances.get(item) / totalSlots;// if 1.0, it will make x item every crafting cycle, if 0.5 it'll be every other cycle... so on and so forth
                        items[i++] = new items(item, selectionChance);
                    }
                    pc.rate = 60f / separator.craftTime;
                    pc.products = items;

                    if (separator.consumes.has(ConsumeType.item) && separator.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        pc.materials = IStoItems(ci.items);
                    }
                }
                //misc.
                if (t.block() instanceof OverdriveProjector op) {
                    if (op.consumes.has(ConsumeType.item) && op.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        itemConsumption(ci.items, op.useTime, ci.optional);
                    }
                } else if (t.block() instanceof MendProjector mp) {
                    if (mp.consumes.has(ConsumeType.item) && mp.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        itemConsumption(ci.items, mp.useTime, ci.optional);
                    }
                }
                apc.add(pc);
                repeat.add(t.build);
            }
        }

        normalizeRates();

        HashMap<Object, finalAverages> isd = new HashMap<>();
        float maxPowerAverageP = 0;
        float maxPowerAverageN = 0;

        for (pc pc : apc) {
            if (pc.products != null) for (items is : pc.products) {
                isd.putIfAbsent(is.item, new finalAverages());
                finalAverages fa = isd.get(is.item);
                fa.production += (is.amount * pc.rate * pc.efficiency);
                fa.machines++;
                fa.efficiencies += pc.efficiency;
            }
            if (pc.materials != null) for (items is : pc.materials) {
                isd.putIfAbsent(is.item, new finalAverages());
                isd.get(is.item).consumption += (is.amount * pc.rate * pc.efficiency);
            }
            if (pc.liquidProduct != null) {
                isd.putIfAbsent(pc.liquidProduct, new finalAverages());
                finalAverages fa = isd.get(pc.liquidProduct);
                fa.production += (pc.liquidProductionRate * pc.efficiency);
                fa.machines++;
                fa.efficiencies += pc.efficiency;
            }
            if (pc.liquidUsage != null) {
                isd.putIfAbsent(pc.liquidUsage, new finalAverages());
                isd.get(pc.liquidUsage).consumption += (pc.liquidUsageRate * pc.efficiency);
            }
            maxPowerAverageP += pc.powerProduction * pc.efficiency;
            maxPowerAverageN -= pc.powerConsumption * pc.efficiency;
        }

        itemPC.forEach((k, v) -> {
            float consumptionOptional = 0f;

            for (pcEntry pce : v) {
                if (pce.optional) {
                    consumptionOptional += pce.consumption;
                }
            }
            isd.put(k, new finalAverages(0f, 0f, consumptionOptional));
        });

        DecimalFormat df = new DecimalFormat("0.00");
        StringBuilder builder = new StringBuilder(label);
        for (Object o : isd.keySet()) {
            finalAverages averages = isd.get(o);
            String emoji = "";
            String name = "";
            String color = "";
            if (o instanceof Item item) {
                emoji = item.emoji();
                name = item.name;
                color = item.color + "";
            } if (o instanceof Liquid liquid) {
                emoji = liquid.emoji();
                name = liquid.name;
                color = liquid.color + "";
            }
            builder.append("\n[white]([lightgray]").append(df.format((averages.efficiencies / averages.machines) * 100f)).append("%[white]) ").append(emoji).append("[#").append(color).append("]").append(name).append(" :");
            float difference = averages.production - averages.consumption;
            if (0.001f > difference && difference > -0.001f) difference = 0f;
            if (averages.production > 0 || averages.consumption > 0) builder.append(difference == 0 ? " [lightgray]" : difference < 0 ? " [scarlet]" : " [lime]+").append(df.format(difference));
            if (averages.production > 0 && averages.consumption > 0) builder.append(" [white]= [lime]+").append(df.format(averages.production)).append(" [white]+ [scarlet]-").append(df.format(averages.consumption));
            if (averages.consumptionOptional > 0) builder.append(" [lightgray](-").append(df.format(averages.consumptionOptional)).append(" Optional)");
        }
        if (maxPowerAverageP > 0 || maxPowerAverageN < 0) {
            builder.append("\n[yellow]Power [white]: ").append(maxPowerAverageP + maxPowerAverageN < 0 ? "[scarlet]" : "[lime]+").append(df.format(maxPowerAverageP + maxPowerAverageN));
            if (maxPowerAverageP > 0 && maxPowerAverageN < 0) builder.append(" [white]= [lime]+").append(df.format(maxPowerAverageP)).append(" [white]").append(maxPowerAverageP > 0 ? "+ " : "= ").append("[scarlet]").append(df.format(maxPowerAverageN));
        }

        if (!builder.toString().equals(label)) formattedMessage = builder.toString();
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

    private static class finalAverages {
        public float production;
        public float consumption;
        public float consumptionOptional;

        public float efficiencies = 0;
        public int machines = 0;

        public finalAverages(float production, float consumption, float consumptionOptional) {
            this.production = production;
            this.consumption = consumption;
            this.consumptionOptional = consumptionOptional;
        }

        public finalAverages() {
            production = 0;
            consumption = 0;
            consumptionOptional = 0;
        }
    }

    private static class pc {
        @Nullable
        public items[] materials;
        @Nullable
        public items[] products;
        @Nullable
        public float rate;
        @Nullable
        public Liquid liquidUsage;
        @Nullable
        public float liquidUsageRate;
        @Nullable
        public Liquid liquidProduct;
        @Nullable
        public float liquidProductionRate;

        @Nullable
        public float powerConsumption;
        @Nullable
        public float powerProduction;

        public float efficiency = 1f;

        public pc () {}
    }

    private static class items {
        public final Item item;
        public final float amount;

        public items(Item item, float amount) {
            this.item = item;
            this.amount = amount;
        }

        public items(ItemStack is) {
            this.item = is.item;
            this.amount = is.amount;
        }
    }

    private static items[] IStoItems(ItemStack[] is) {
        items[] out = new items[is.length];
        for (int i = 0; i < is.length; i++) out[i] = new items(is[i].item, is[i].amount);
        return out;
    }

    private static class sd {
        public float supply = 0f;
        public float demand = 0f;

        public sd() {}
    }

    private void normalizeRates() {
        boolean finalResult = false;
        for (int i = 0; i < 30 && !finalResult; i++) { //tried while loop but it sucks ass
            if (i == 29) Log.err("normalizeRates looped 30 times!");
            HashMap<Object, Float> pcr = getPCRatio();
            if (!pcr.isEmpty()) {
                float max = -1;
                Object maxItem = null;
                for (Object item : pcr.keySet()) {
                    float ratio = pcr.get(item);
                    if (ratio > max) {
                        maxItem = item;
                        max = ratio;
                    }
                }
                if (maxItem != null) {
                    if (max != 0 && max < 1f) {
                        //go through all consumptions. whatever consumes the under-produced item gets slowed down enough to meet production
                        if (maxItem instanceof Item maximum) {
                            for (pc pc : apc) {
                                if (pc.materials != null) {
                                    for (items is : pc.materials) {
                                        if (is.item == maximum) {
                                            float p = pc.efficiency;
                                            pc.efficiency *= max;
                                        }
                                    }
                                }
                            }
                        } else if (maxItem instanceof Liquid maximum) {
                            for (pc pc : apc) {
                                if (pc.liquidUsage != null) {
                                    if (pc.liquidUsage == maximum) {
                                        float p = pc.efficiency;
                                        pc.efficiency *= max;
                                    }
                                }
                            }
                        }
                    } else if (max > 1f) {
                        float ratio = 1f / max;
                        //go through all production. whatever produces the overproduced item gets slowed down enough to meet demand
                        if (maxItem instanceof Item item) {
                            for (pc pc : apc) {
                                if (pc.products != null) {
                                    for (items is : pc.products) {
                                        if (is.item == item) {
                                            float p = pc.efficiency;
                                            pc.efficiency *= ratio;
                                        }
                                    }
                                }
                            }
                        } else if (maxItem instanceof Liquid liquid) {
                            for (pc pc : apc) {
                                if (pc.liquidProduct != null) {
                                    if (pc.liquidProduct == liquid) {
                                        float p = pc.efficiency;
                                        pc.efficiency *= ratio;
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                finalResult = true;
            }
        }
    }

    private HashMap<Object, Float> getPCRatio() {
        HashMap<Object, Float> pcr = new HashMap<>();
        getSD().forEach((item, sd) -> {
            if (sd.demand > 0) {
                float ratio = sd.supply / sd.demand;
                if ( !(1.001f > ratio && ratio > 0.999f) && ratio > 0.001f) pcr.put(item, ratio); //don't attempt to fix if perfect or 0.001% off
            }
        });
        return pcr;
    }

    private HashMap<Object, sd> getSD() {
        HashMap<Object, sd> isd = new HashMap<>();
        for (pc pc : apc) {
            if (pc.products != null) for (items is : pc.products) {
                isd.putIfAbsent(is.item, new sd());
                isd.get(is.item).supply += (is.amount * pc.rate * pc.efficiency);
            }
            if (pc.materials != null) for (items is : pc.materials) {
                isd.putIfAbsent(is.item, new sd());
                isd.get(is.item).demand += (is.amount * pc.rate * pc.efficiency);
            }
            if (pc.liquidProduct != null) {
                isd.putIfAbsent(pc.liquidProduct, new sd());
                isd.get(pc.liquidProduct).supply += (pc.liquidProductionRate * pc.efficiency);
            }
            if (pc.liquidUsage != null) {
                isd.putIfAbsent(pc.liquidUsage, new sd());
                isd.get(pc.liquidUsage).demand += (pc.liquidUsageRate * pc.efficiency);
            }
        }

        if (intermediate.isEmpty()) {
            isd.forEach((item, sd) -> {
                if (sd.supply != 0 && sd.demand != 0) {
                    intermediate.add(item);
                }
            });
        }

        return isd;
    }

    private void itemConsumption(ItemStack[] stacks, float interval, boolean optional) {
        for (ItemStack is : stacks) {
            itemPC.putIfAbsent(is.item, new ArrayList<>());
            itemPC.get(is.item).add(new pcEntry(0, is.amount / (interval / 60f), optional));
        }
    }
}
