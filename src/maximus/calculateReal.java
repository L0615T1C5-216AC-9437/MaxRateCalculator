package maximus;

import arc.graphics.Color;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Nullable;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Iconc;
import mindustry.type.*;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.MendProjector;
import mindustry.world.blocks.defense.OverdriveProjector;
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
import java.util.List;

import static maximus.mrc.translatedStringOptional;
import static maximus.mrc.translatedStringPower;
import static mindustry.Vars.world;

public class calculateReal extends mrc.calculation {
    private final ArrayList<pc> apc = new ArrayList<>();
    private Item bestFlammableFuel = null;
    private Item bestRadioactiveFuel = null;
    //
    public static String translatedStringLabel = "";
    public static String translatedStringPowerGeneration = "";
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
                if (t.block() instanceof BurnerGenerator bg) {
                    pc.usesFlammableItem = true;
                    pc.rate = 60f / bg.itemDuration;
                } else if (t.block() instanceof DecayGenerator dg) {
                    pc.usesRadioactiveItem = true;
                    pc.rate = 60f / dg.itemDuration;
                } else if (t.block() instanceof ItemLiquidGenerator ilg) {
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
                            pc.liquidProductionRate = gc.outputLiquid.amount * (60f / gc.craftTime);
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
                //Unit Factory
                if (t.block() instanceof UnitFactory uf && t.build instanceof UnitFactory.UnitFactoryBuild ufb) {
                    if (ufb.currentPlan != -1) {
                        UnitFactory.UnitPlan up = uf.plans.get(ufb.currentPlan);
                        pc.unitProduct = up.unit;
                        pc.materials = IStoItems(up.requirements);
                        pc.rate = 3600f / up.time;
                    }
                }
                if (t.block() instanceof Reconstructor r) {
                    pc.isReconstructor = true;
                    pc.upgrades = r.upgrades;
                    if (r.consumes.has(ConsumeType.item) && r.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        pc.materials = IStoItems(ci.items);
                        pc.rate = 3600f / r.constructTime;
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
        float powerAverageP = 0f;
        float powerAverageN = 0f;
        float powerEfficiencies = 0f;
        int powerProducers = 0;

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
            if (pc.unitProduct != null) {
                isd.putIfAbsent(pc.unitProduct, new finalAverages());
                finalAverages fa = isd.get(pc.unitProduct);
                fa.production += (pc.rate * pc.efficiency);
                fa.machines++;
                fa.efficiencies += pc.efficiency;
            }
            if (pc.unitUsed != null) {
                isd.putIfAbsent(pc.unitUsed, new finalAverages());
                isd.get(pc.unitUsed).consumption += (pc.rate * pc.efficiency);
            }

            if (pc.powerProduction > 0) {
                powerEfficiencies += pc.efficiency;
                powerProducers++;
            }
            powerAverageP += pc.powerProduction * pc.efficiency;
            powerAverageN -= pc.powerConsumption * pc.efficiency;
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
        StringBuilder builder = new StringBuilder(translatedStringLabel);
        for (Object o : isd.keySet()) {
            finalAverages averages = isd.get(o);
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
            builder.append("\n[white]([lightgray]").append(df.format((averages.efficiencies / averages.machines) * 100f)).append("%[white]) ").append(emoji).append("[#").append(color).append("]").append(name).append(" :");
            float difference = averages.production - averages.consumption;
            if (0.001f > difference && difference > -0.001f) difference = 0f;
            if (averages.production > 0 || averages.consumption > 0) builder.append(difference == 0 ? " [lightgray]" : difference < 0 ? " [scarlet]" : " [lime]+").append(df.format(difference));
            if (averages.production > 0 && averages.consumption > 0) builder.append(" [white]= [lime]+").append(df.format(averages.production)).append(" [white]+ [scarlet]-").append(df.format(averages.consumption));
            if (averages.consumptionOptional > 0) builder.append(" [lightgray](-").append(df.format(averages.consumptionOptional)).append(" ").append(translatedStringOptional).append(")");
        }
        if (powerAverageP > 0) {
            builder.append("\n[white]([lightgray]").append(df.format((powerEfficiencies / powerProducers) * 100f)).append("%[white]) ").append(Iconc.power).append("[yellow]").append(translatedStringPowerGeneration).append(" : [lime]+").append(powerAverageP);
        }
        if (powerAverageP > 0 || powerAverageN < 0) {
            builder.append("\n[yellow]").append(translatedStringPower).append(" : [white]").append(powerAverageP + powerAverageN < 0 ? "[scarlet]" : "[lime]+").append(df.format(powerAverageP + powerAverageN));
            if (powerAverageP > 0 && powerAverageN < 0) builder.append(" [white]= [lime]+").append(df.format(powerAverageP)).append(" [white]").append(powerAverageP > 0 ? "+ " : "= ").append("[scarlet]").append(df.format(powerAverageN));
        }

        if (!builder.toString().equals(translatedStringLabel)) formattedMessage = builder.toString();
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
        public float rate;

        @Nullable
        public Liquid liquidUsage;
        public float liquidUsageRate;
        @Nullable
        public Liquid liquidProduct;
        public float liquidProductionRate;
        @Nullable
        public UnitType unitProduct;
        public UnitType unitUsed;
        public boolean isReconstructor;
        public Seq<UnitType[]> upgrades;

        public boolean usesFlammableItem;
        public boolean usesRadioactiveItem;

        public float powerConsumption;
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
            if (i == 29) Log.err(mrc.bundle.getString("calculateReal.tookTooLong"));
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
                                            pc.efficiency *= max;
                                        }
                                    }
                                }
                            }
                        } else if (maxItem instanceof Liquid maximum) {
                            for (pc pc : apc) {
                                if (pc.liquidUsage != null) {
                                    if (pc.liquidUsage == maximum) {
                                        pc.efficiency *= max;
                                    }
                                }
                            }
                        } else if (maxItem instanceof UnitType maximum) {
                            for (pc pc : apc) {
                                if (pc.unitUsed != null) {
                                    if (pc.unitUsed == maximum) {
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
                                            pc.efficiency *= ratio;
                                        }
                                    }
                                }
                            }
                        } else if (maxItem instanceof Liquid liquid) {
                            for (pc pc : apc) {
                                if (pc.liquidProduct != null) {
                                    if (pc.liquidProduct == liquid) {
                                        pc.efficiency *= ratio;
                                    }
                                }
                            }
                        } else if (maxItem instanceof UnitType unit) {
                            for (pc pc : apc) {
                                if (pc.unitProduct != null) {
                                    if (pc.unitProduct == unit) {
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
                if ( !(1.001f > ratio && ratio > 0.999f) && ratio > 0f) pcr.put(item, ratio); //don't attempt to fix if perfect or 0.001% off
            }
        });
        return pcr;
    }

    private HashMap<Object, sd> getSD() {
        HashMap<Object, sd> isd = new HashMap<>();
        float flammableFuelDemand = 0f;
        float radioactiveFuelDemand = 0f;
        HashMap<UnitType, UnitType> reconstructorUpgrades = new HashMap<>();
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
            if (pc.unitProduct != null) {
                isd.putIfAbsent(pc.unitProduct, new sd());
                isd.get(pc.unitProduct).supply += (pc.rate * pc.efficiency);
            }
            if (pc.unitUsed != null) {
                isd.putIfAbsent(pc.unitUsed, new sd());
                isd.get(pc.unitUsed).demand += (pc.rate * pc.efficiency);
            }
            if (pc.isReconstructor && pc.upgrades != null && pc.unitUsed == null) {
                for (UnitType[] upgrade : pc.upgrades) {
                    reconstructorUpgrades.put(upgrade[0], upgrade[1]);
                }
            }
            if (pc.usesFlammableItem && pc.materials == null) {
                flammableFuelDemand += (pc.rate * pc.efficiency);
            }
            if (pc.usesRadioactiveItem && pc.materials == null) {
                radioactiveFuelDemand += (pc.rate * pc.efficiency);
            }
        }
        //
        if (!reconstructorUpgrades.isEmpty()) {
            UnitType a = null;
            UnitType b = null;
            for (Object o : isd.keySet()) {
                if (o instanceof UnitType unit) {
                    if (reconstructorUpgrades.containsKey(unit)) {
                        a = unit;
                        b = reconstructorUpgrades.get(unit);
                        break;
                    }
                }
            }
            if (a == null) {
                for (UnitType c : reconstructorUpgrades.keySet()) {
                    a = c;
                    b = reconstructorUpgrades.get(c);
                    break;
                }
            }
            if (a != null && b != null) {
                isd.putIfAbsent(a, new sd());
                isd.putIfAbsent(b, new sd());
                for (pc pc : apc) {
                    if (pc.isReconstructor) {
                        for (UnitType[] upgrade : pc.upgrades) {
                            if (upgrade[0] == a) {
                                isd.get(a).demand += pc.rate;
                                isd.get(b).supply += pc.rate;

                                pc.unitUsed = a;
                                pc.unitProduct = b;
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (flammableFuelDemand > 0f && bestFlammableFuel == null) {
            float bestFlammability = -1;
            Item bestFlammable = null;
            for (Object o : isd.keySet()) { //will assume items being used for production can be used as fuel
                if (o instanceof Item item) {
                    if (item.flammability > 0f && item.flammability > bestFlammability && item.explosiveness < 0.5f) { //explosiveness over 0.5f can cause turbine damage depending on settings
                        bestFlammable = item;
                        bestFlammability = item.flammability;
                    }
                }
            }
            bestFlammableFuel = bestFlammable;
        }
        if (flammableFuelDemand > 0f && bestFlammableFuel != null) {
            isd.putIfAbsent(bestFlammableFuel, new sd());
            isd.get(bestFlammableFuel).demand += flammableFuelDemand;

            for (pc pc : apc) {
                if (pc.usesFlammableItem) {
                    pc.materials = new items[] { new items(bestFlammableFuel, 1) };
                }
            }
        }
        if (radioactiveFuelDemand > 0f && bestRadioactiveFuel == null) {
            float bestRadioactivity = -1;
            Item bestRadioactive = null;
            for (Object o : isd.keySet()) {
                if (o instanceof Item item) {
                    if (item.radioactivity > 0f && item.radioactivity > bestRadioactivity) { //explosiveness over 0.5f can cause turbine damage depending on settings
                        bestRadioactive = item;
                        bestRadioactivity = item.radioactivity;
                    }
                }
            }
            bestRadioactiveFuel = bestRadioactive;
        }
        if (radioactiveFuelDemand > 0f && bestRadioactiveFuel != null) {
            isd.putIfAbsent(bestRadioactiveFuel, new sd());
            isd.get(bestRadioactiveFuel).demand += radioactiveFuelDemand;

            for (pc pc : apc) {
                if (pc.usesRadioactiveItem) {
                    pc.materials = new items[] { new items(bestRadioactiveFuel, 1) };
                }
            }
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
