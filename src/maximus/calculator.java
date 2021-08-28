package maximus;

import arc.Core;
import arc.graphics.Color;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Nullable;
import mindustry.Vars;
import mindustry.content.Liquids;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Iconc;
import mindustry.type.*;
import mindustry.ui.Menus;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.MendProjector;
import mindustry.world.blocks.defense.OverdriveProjector;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.blocks.units.RepairPoint;
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

public class calculator {
    public String formattedMessage = "";
    //
    public final int xl;
    public final int xr;
    public final int yb;
    public final int yt;
    public final boolean rateLimit;
    //
    private final ArrayList<pc> apc = new ArrayList<>();
    private Item bestFlammableFuel = null;
    private Item bestRadioactiveFuel = null;
    //
    public static String translatedStringMaxTitle = "";
    public static String translatedStringRealTitle = "";
    public static String translatedStringPowerGeneration = "";
    //old
    public final HashMap<Item, List<pcEntry>> itemPC;

    public calculator(int x1, int y1, int x2, int y2, boolean rateLimit) throws Exception {
        xl = Math.min(x1, x2);
        xr = Math.max(x1, x2);
        yb = Math.min(y1, y2);
        yt = Math.max(y1, y2);
        this.rateLimit = rateLimit;
        itemPC = new HashMap<>();

        if (xl < 0 || yb < 0 || xr > Vars.world.width() || yt > Vars.world.height()) throw new Exception("Invalid Coordinates");

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
                        if (rateLimit && boost == 1f) {
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
                if (t.block() instanceof  ThermalGenerator tg) {
                    Tile tile = t.build.tile; //get origin block
                    float efficiency = tg.sumAttribute(tg.attribute, tile.x, tile.y);
                    pc.powerProduction *= efficiency;
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
                        pc.rate = 60f / up.time;
                    }
                }
                if (t.block() instanceof Reconstructor r) {
                    pc.isReconstructor = true;
                    pc.upgrades = r.upgrades;
                    if (r.consumes.has(ConsumeType.item) && r.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        pc.materials = IStoItems(ci.items);
                        pc.rate = 60f / r.constructTime;
                    }
                }
                //weapons
                if (t.block() instanceof ReloadTurret rt) {
                    pc.rate = 60f / rt.reloadTime;
                }
                if (t.block() instanceof LiquidTurret lt && t.build instanceof LiquidTurret.LiquidTurretBuild ltb) {
                    float liquidUsage = 60f / lt.reloadTime;
                    if (ltb.liquids.total() > 0) { //calculate liquid buff if has liquid
                        pc.liquidUsage = ltb.liquids.current(); //current coolant
                        BulletType type = lt.ammoTypes.get(ltb.liquids().current());
                        pc.liquidUsageRate = liquidUsage / type.ammoMultiplier; //buff rate by liquid calculation
                    } else if (!rateLimit) { //if calculate max flag for buff
                        pc.liquidUsageRate = liquidUsage;
                        pc.liquidAmmoTypes = lt.ammoTypes;
                    }
                } else if (t.block() instanceof Turret turret && t.build instanceof Turret.TurretBuild tb) {
                    if (tb.liquids.total() > 0) { //calculate liquid buff if has liquid
                        pc.liquidUsage = tb.liquids.current(); //current coolant
                        pc.liquidUsageRate = turret.coolantUsage * 60f; //liquid/s
                        pc.rate *= 1 + (turret.coolantUsage * tb.liquids.current().heatCapacity * turret.coolantMultiplier); //buff rate by liquid calculation
                    } else if (t.block() instanceof LaserTurret || !rateLimit) { //if calculate max flag for buff
                        pc.liquidUsageRate = turret.coolantUsage * 60f; //liquid/s
                    }
                }
                if (t.block() instanceof ItemTurret it) {
                    pc.ammoTypes = it.ammoTypes;
                    pc.rate *= it.burstSpacing > 0.0001f ? it.shots : it.ammoPerShot; //burst uses ammo per shot, everything else is per firing.

                    if (t.build instanceof ItemTurret.ItemTurretBuild itb && !itb.ammo.isEmpty()) {
                        var bt = itb.peekAmmo();
                        for (Item item : it.ammoTypes.keys()) {
                            if (bt == it.ammoTypes.get(item)) {
                                pc.materials = new items[] { new items(item, 1) };
                                pc.rate /= bt.ammoMultiplier;
                                pc.rate *= bt.reloadMultiplier;
                                break;
                            }
                        }
                    }
                }

                if (t.block() instanceof BaseTurret bt) {
                    pc.coolantMultiplier = bt.coolantMultiplier;
                }
                if (t.block() instanceof RepairPoint rp) {
                    if (rp.acceptCoolant) {
                        pc.liquidUsageRate = rp.coolantUse * 60f;
                        pc.coolantMultiplier = rp.coolantMultiplier;
                    }
                }
                //misc.
                if (t.block() instanceof OverdriveProjector op) {
                    if (op.consumes.has(ConsumeType.item) && op.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        itemConsumption(ci.items, op.useTime, true);
                    }
                } else if (t.block() instanceof MendProjector mp) {
                    if (mp.consumes.has(ConsumeType.item) && mp.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        itemConsumption(ci.items, mp.useTime, true);
                    }
                }
                apc.add(pc);
                repeat.add(t.build);
            }
        }

        if (rateLimit) {
            normalizeRates();
        } else {
            getSD();
        }

        HashMap<Object, finalAverages> isd = new HashMap<>();
        float powerAverageP = 0f;
        float powerAverageN = 0f;
        float powerEfficiencies = 0f;
        int powerProducers = 0;

        for (pc pc : apc) {
            if (pc.products != null) for (items is : pc.products) {
                isd.putIfAbsent(is.item, new finalAverages());
                finalAverages fa = isd.get(is.item);
                fa.maxProduction += is.amount * pc.rate;
                fa.production += (is.amount * pc.rate * pc.efficiency);
            }
            if (pc.materials != null) for (items is : pc.materials) {
                isd.putIfAbsent(is.item, new finalAverages());
                isd.get(is.item).consumption += (is.amount * pc.rate * pc.efficiency);
            }
            if (pc.liquidProduct != null) {
                isd.putIfAbsent(pc.liquidProduct, new finalAverages());
                finalAverages fa = isd.get(pc.liquidProduct);
                fa.maxProduction += pc.liquidProductionRate;
                fa.production += (pc.liquidProductionRate * pc.efficiency);
            }
            if (pc.liquidUsage != null) {
                isd.putIfAbsent(pc.liquidUsage, new finalAverages());
                isd.get(pc.liquidUsage).consumption += (pc.liquidUsageRate * pc.efficiency);
            }
            if (pc.unitProduct != null) {
                isd.putIfAbsent(pc.unitProduct, new finalAverages());
                finalAverages fa = isd.get(pc.unitProduct);
                fa.maxProduction += pc.rate;
                fa.production += (pc.rate * pc.efficiency);
            }
            if (pc.unitUsage != null) {
                isd.putIfAbsent(pc.unitUsage, new finalAverages());
                isd.get(pc.unitUsage).consumption += (pc.rate * pc.efficiency);
            }

            if (pc.powerProduction > 0) {
                powerEfficiencies += pc.efficiency;
                powerProducers++;
            }
            powerAverageP += pc.powerProduction * pc.efficiency;
            powerAverageN -= pc.powerConsumption * pc.efficiency;
        }

        itemPC.forEach((k, v) -> {
            float consumption = 0f;
            float consumptionOptional = 0f;

            for (pcEntry pce : v) {
                if (pce.optional) {
                    consumptionOptional += pce.consumption;
                } else {
                    consumption += pce.consumption;
                }
            }
            isd.putIfAbsent(k, new finalAverages());
            finalAverages fa = isd.get(k);
            fa.consumption += consumption;
            fa.consumptionOptional += consumptionOptional;
        });

        DecimalFormat df = new DecimalFormat("0.00");
        String title = rateLimit ? translatedStringRealTitle : translatedStringMaxTitle;
        StringBuilder builder = new StringBuilder(title);
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
            builder.append("\n[white]");
            if (rateLimit) {
                builder.append("([lightgray]").append(df.format((averages.production / averages.maxProduction) * 100f)).append("%[white]) ");
            }
            builder.append(emoji).append("[#").append(color).append("]").append(name).append(" :");
            float difference = averages.production - averages.consumption;
            if (o instanceof UnitType) difference *= 60f;
            if (0.001f > difference && difference > -0.001f) difference = 0f;
            if (averages.production > 0 || averages.consumption > 0) builder.append(difference == 0f ? " [lightgray]" : difference < 0 ? " [scarlet]" : " [lime]+").append(df.format(difference));
            if ((Core.settings.getBool("mrcShowZeroAverageMath", true) || difference != 0f) && averages.production > 0 && averages.consumption > 0) builder.append(" [white]= [lime]+").append(df.format(averages.production)).append(" [white]+ [scarlet]-").append(df.format(averages.consumption));
            if (averages.consumptionOptional > 0) builder.append(" [lightgray](-").append(df.format(averages.consumptionOptional)).append(" ").append(translatedStringOptional).append(")");
        }
        if (powerAverageP > 0) {
            builder.append("\n[white]([lightgray]").append(df.format((powerEfficiencies / powerProducers) * 100f)).append("%[white]) ").append(Iconc.power).append("[yellow]").append(translatedStringPowerGeneration).append(" : [lime]+").append(powerAverageP);
        }
        if (powerAverageP > 0 || powerAverageN < 0) {
            builder.append("\n[yellow]").append(translatedStringPower).append(" : [white]").append(powerAverageP + powerAverageN < 0 ? "[scarlet]" : "[lime]+").append(df.format(powerAverageP + powerAverageN));
            if (powerAverageP > 0 && powerAverageN < 0) builder.append(" [white]= [lime]+").append(df.format(powerAverageP)).append(" [white]").append(powerAverageP > 0 ? "+ " : "= ").append("[scarlet]").append(df.format(powerAverageN));
        }

        if (!builder.toString().equals(title)) formattedMessage = builder.toString();
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
        public float maxProduction;
        public float consumption;
        public float consumptionOptional;


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
        public UnitType unitUsage;
        public boolean isReconstructor;
        public Seq<UnitType[]> upgrades;

        public boolean usesFlammableItem;
        public boolean usesRadioactiveItem;

        public ObjectMap<Item, BulletType> ammoTypes;
        public ObjectMap<Liquid, BulletType> liquidAmmoTypes;
        public float coolantMultiplier;

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
        for (int i = 0; i < 60; i++) {
            if (i == 59) Log.err(mrc.bundle.getString("calculateReal.tookTooLong"));
            HashMap<Object, Float> pcr = getPCRatio();
            if (!pcr.isEmpty()) {
                float max = -1;
                Object maxObject = null;
                for (Object object : pcr.keySet()) {
                    float ratio = pcr.get(object);
                    if (!(1.00001f > ratio && ratio > 0.99999f) && ratio > max) {
                        maxObject = object;
                        max = ratio;
                    }
                }
                if (maxObject != null) {
                    if (max != 0 && max < 1f) {
                        //go through all consumptions. whatever consumes the under-produced item gets slowed down enough to meet production
                        if (maxObject instanceof Item item) {
                            for (pc pc : apc) {
                                if (pc.materials != null) {
                                    for (items is : pc.materials) {
                                        if (is.item == item) {
                                            pc.efficiency *= max;
                                        }
                                    }
                                }
                            }
                        } else if (maxObject instanceof Liquid liquid) {
                            for (pc pc : apc) {
                                if (pc.liquidUsage != null) {
                                    if (pc.liquidUsage == liquid) {
                                        pc.efficiency *= max;
                                    }
                                }
                            }
                        } else if (maxObject instanceof UnitType unit) {
                            for (pc pc : apc) {
                                if (pc.unitUsage != null) {
                                    if (pc.unitUsage == unit) {
                                        pc.efficiency *= max;
                                    }
                                }
                            }
                        }
                    } else if (max > 1f) {
                        float ratio = 1f / max;
                        //go through all production. whatever produces the overproduced item gets slowed down enough to meet demand
                        if (maxObject instanceof Item item) {
                            for (pc pc : apc) {
                                if (pc.products != null) {
                                    for (items is : pc.products) {
                                        if (is.item == item) {
                                            pc.efficiency *= ratio;
                                        }
                                    }
                                }
                            }
                        } else if (maxObject instanceof Liquid liquid) {
                            for (pc pc : apc) {
                                if (pc.liquidProduct != null) {
                                    if (pc.liquidProduct == liquid) {
                                        pc.efficiency *= ratio;
                                    }
                                }
                            }
                        } else if (maxObject instanceof UnitType unit) {
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
                float closest = 1f;
                var sdhr = getSD();
                var sdhm = getMaxSD();
                for (Object o : sdhm.keySet()) {
                    sd sdm = sdhm.get(o);
                    sd sdr = sdhr.get(o);
                    float max = sdm.supply;
                    float real = sdr.supply;
                    float proximity = (max - real) / max;
                    if (proximity < closest) {
                        closest = proximity;
                    }
                }

                if (0.00001f > closest && closest > -0.00001f) {
                    break;
                } else if (closest > 0) {
                    float fix = 1 / (1 - closest);
                    for (pc pc : apc) {
                        pc.efficiency = Math.min(1f, pc.efficiency * fix);
                    }
                }
            }
        }
    }

    private HashMap<Object, Float> getPCRatio() {
        HashMap<Object, Float> pcr = new HashMap<>();
        getSD().forEach((object, sd) -> {
            if (sd.supply > 0 && sd.demand > 0) {
                float ratio = sd.supply / sd.demand;
                if (!(1.00001f > ratio && ratio > 0.99999f)) { //don't attempt to fix if perfect or 0.001% off
                    pcr.put(object, ratio);
                }
            }
        });
        return pcr;
    }

    private HashMap<Object, sd> getSD() {
        HashMap<Object, sd> isd = new HashMap<>();
        float flammableFuelDemand = 0f;
        float radioactiveFuelDemand = 0f;
        HashMap<UnitType, UnitType> reconstructorUpgrades = new HashMap<>();
        ArrayList<pc> turrets = new ArrayList<>();
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
            if (pc.unitUsage != null) {
                isd.putIfAbsent(pc.unitUsage, new sd());
                isd.get(pc.unitUsage).demand += (pc.rate * pc.efficiency);
            }
            if (pc.isReconstructor && pc.upgrades != null && pc.unitUsage == null) {
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
            if (pc.ammoTypes != null && pc.materials == null) {
                turrets.add(pc);
            } else if (pc.liquidUsageRate > 0f && pc.coolantMultiplier > 0f && pc.liquidUsage == null) {
                turrets.add(pc);
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

                                pc.unitUsage = a;
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
        if (!turrets.isEmpty()) {
            Liquid bestCoolant = null;
            float maxHeatCapacity = -1f;
            for (Object o : isd.keySet()) {
                if (o instanceof Liquid l) {
                    if (l.flammability == 0f && l.heatCapacity > maxHeatCapacity) {
                        bestCoolant = l;
                        maxHeatCapacity = l.heatCapacity;
                    }
                }
            }
            if (bestCoolant == null && !rateLimit) {
                //imagine cryofluid is available when calculating max and no other coolant is produced locally
                bestCoolant = Liquids.cryofluid;
                maxHeatCapacity = Liquids.cryofluid.heatCapacity;
            }

            for (pc pc : turrets) {
                if (pc.materials == null && pc.ammoTypes != null) { //if no ammo is loaded and uses ammo
                    for (Item item : pc.ammoTypes.keys()) { //will use item that's being produced/consumed
                        if (isd.containsKey(item)) {
                            pc.materials = new items[]{new items(item, 1)};
                            //no `break;` here so it prefers items further down the list (normally better ammo)
                        }
                    }

                    if (pc.materials == null) {
                        //get best ammo if no ammo is locally produced/used
                        for (Item item : pc.ammoTypes.keys()) { //shit implementation
                            pc.materials = new items[] { new items(item, 1) };
                        }
                    }

                    if (pc.materials != null) {
                        var bt = pc.ammoTypes.get(pc.materials[0].item);
                        pc.rate /= bt.ammoMultiplier;
                        pc.rate *= bt.reloadMultiplier;
                    }
                }

                if (pc.liquidUsage == null && bestCoolant != null && pc.liquidUsageRate > 0f) { //should only happen on calculate max or repair point
                    pc.liquidUsage = bestCoolant;
                    if (pc.liquidAmmoTypes != null) {
                        pc.liquidUsageRate /= pc.liquidAmmoTypes.get(bestCoolant).ammoMultiplier;
                    } else if (pc.coolantMultiplier > 0) {
                        pc.rate *= 1 + ((pc.liquidUsageRate / 60f) * maxHeatCapacity * pc.coolantMultiplier); //buff rate by liquid calculation
                    }
                }

                //add consumptions
                if (pc.materials != null) {
                    isd.putIfAbsent(pc.materials[0], new sd());
                    isd.get(pc.materials[0]).demand += (pc.rate * pc.efficiency);
                }
                if (pc.liquidUsage != null) {
                    isd.putIfAbsent(pc.liquidUsage, new sd());
                    isd.get(pc.liquidUsage).demand += (pc.liquidUsageRate * pc.efficiency);
                }
            }
        }

        return isd;
    }
    private HashMap<Object, sd> getMaxSD() {
        HashMap<Object, sd> isd = new HashMap<>();
        float flammableFuelDemand = 0f;
        float radioactiveFuelDemand = 0f;
        HashMap<UnitType, UnitType> reconstructorUpgrades = new HashMap<>();
        ArrayList<pc> turrets = new ArrayList<>();
        for (pc pc : apc) {
            if (pc.products != null) for (items is : pc.products) {
                isd.putIfAbsent(is.item, new sd());
                isd.get(is.item).supply += (is.amount * pc.rate);
            }
            if (pc.materials != null) for (items is : pc.materials) {
                isd.putIfAbsent(is.item, new sd());
                isd.get(is.item).demand += (is.amount * pc.rate);
            }
            if (pc.liquidProduct != null) {
                isd.putIfAbsent(pc.liquidProduct, new sd());
                isd.get(pc.liquidProduct).supply += (pc.liquidProductionRate);
            }
            if (pc.liquidUsage != null) {
                isd.putIfAbsent(pc.liquidUsage, new sd());
                isd.get(pc.liquidUsage).demand += (pc.liquidUsageRate);
            }
            if (pc.unitProduct != null) {
                isd.putIfAbsent(pc.unitProduct, new sd());
                isd.get(pc.unitProduct).supply += (pc.rate);
            }
            if (pc.unitUsage != null) {
                isd.putIfAbsent(pc.unitUsage, new sd());
                isd.get(pc.unitUsage).demand += (pc.rate);
            }
            if (pc.isReconstructor && pc.upgrades != null && pc.unitUsage == null) {
                for (UnitType[] upgrade : pc.upgrades) {
                    reconstructorUpgrades.put(upgrade[0], upgrade[1]);
                }
            }
            if (pc.usesFlammableItem && pc.materials == null) {
                flammableFuelDemand += (pc.rate);
            }
            if (pc.usesRadioactiveItem && pc.materials == null) {
                radioactiveFuelDemand += (pc.rate);
            }
            if (pc.ammoTypes != null && pc.materials == null) {
                turrets.add(pc);
            } else if (pc.liquidUsageRate > 0f && pc.coolantMultiplier > 0f && pc.liquidUsage == null) {
                turrets.add(pc);
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

                                pc.unitUsage = a;
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
        if (!turrets.isEmpty()) {
            Liquid bestCoolant = null;
            float maxHeatCapacity = -1f;
            for (Object o : isd.keySet()) {
                if (o instanceof Liquid l) {
                    if (l.flammability == 0f && l.heatCapacity > maxHeatCapacity) {
                        bestCoolant = l;
                        maxHeatCapacity = l.heatCapacity;
                    }
                }
            }
            if (bestCoolant == null && !rateLimit) {
                //imagine cryofluid is available when calculating max and no other coolant is produced locally
                bestCoolant = Liquids.cryofluid;
                maxHeatCapacity = Liquids.cryofluid.heatCapacity;
            }

            for (pc pc : turrets) {
                if (pc.materials == null && pc.ammoTypes != null) { //if no ammo is loaded and uses ammo
                    for (Item item : pc.ammoTypes.keys()) { //will use item that's being produced/consumed
                        if (isd.containsKey(item)) {
                            pc.materials = new items[]{new items(item, 1)};
                            //no `break;` here so it prefers items further down the list (normally better ammo)
                        }
                    }

                    if (pc.materials == null) {
                        //get best ammo if no ammo is locally produced/used
                        for (Item item : pc.ammoTypes.keys()) { //shit implementation
                            pc.materials = new items[] { new items(item, 1) };
                        }
                    }

                    if (pc.materials != null) {
                        var bt = pc.ammoTypes.get(pc.materials[0].item);
                        pc.rate /= bt.ammoMultiplier;
                        pc.rate *= bt.reloadMultiplier;
                    }
                }

                if (pc.liquidUsage == null && bestCoolant != null && pc.liquidUsageRate > 0f) { //should only happen on calculate max or repair point
                    pc.liquidUsage = bestCoolant;
                    pc.rate *= 1 + ((pc.liquidUsageRate / 60f) * maxHeatCapacity * pc.coolantMultiplier); //buff rate by liquid calculation
                }

                //add consumptions
                if (pc.materials != null) {
                    isd.putIfAbsent(pc.materials[0], new sd());
                    isd.get(pc.materials[0]).demand += (pc.rate);
                }
                if (pc.liquidUsage != null) {
                    isd.putIfAbsent(pc.liquidUsage, new sd());
                    isd.get(pc.liquidUsage).demand += (pc.liquidUsageRate);
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

    public void callLabel() {
        Menus.label(formattedMessage, 30, (xl + xr) * 4f, (yb - 5) * 8f);
    }

    public void callInfoMessage() {
        Vars.ui.showInfo(formattedMessage);
    }
}
