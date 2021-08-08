package maximus;

import arc.struct.Seq;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static mindustry.Vars.world;

public class calculateMax extends mrc.calculation {
    public final HashMap<Item, List<pcEntry>> itemPC ;
    public final HashMap<Liquid, List<pcEntry>> liquidPC;
    public final List<Float> powerPC;
    //
    private static final String label = "Max Ratios\n[orange]=========================[white]";

    public calculateMax(int x1, int y1, int x2, int y2) throws Exception {
        super(x1, y1, x2, y2);

        itemPC = new HashMap<>();
        liquidPC = new HashMap<>();
        powerPC = new ArrayList<>();

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
                //generic
                if (t.block().consumes.has(ConsumeType.power) && t.block().consumes.get(ConsumeType.power) instanceof ConsumePower cp) {
                    powerConsumption(cp.usage);
                }
                if (t.block().consumes.has(ConsumeType.liquid) && t.block().consumes.get(ConsumeType.liquid) instanceof ConsumeLiquid cl) {
                    liquidConsumption(cl.liquid, cl.amount);
                }
                //drills
                if (t.block() instanceof Drill d && t.build instanceof Drill.DrillBuild db) {
                    if (db.dominantItems > 0) {
                        float perSecond = db.dominantItems * (db.liquids.total() > 0 ? d.liquidBoostIntensity * d.liquidBoostIntensity : 1f);
                        float difficulty = d.drillTime + d.hardnessDrillMultiplier * db.dominantItem.hardness;
                        float rate = perSecond / difficulty;

                        itemPC.putIfAbsent(db.dominantItem, new ArrayList<>());
                        itemPC.get(db.dominantItem).add(new pcEntry(rate * 60f, 0, false));
                    }
                }
                if (t.block() instanceof Fracker fracker) {
                    if (fracker.consumes.has(ConsumeType.item) && fracker.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        itemConsumption(ci.items, fracker.itemUseTime);
                    }
                }
                //liquids
                if (t.block() instanceof SolidPump sp && t.build instanceof SolidPump.SolidPumpBuild spb) {
                    float fraction = Math.max(spb.validTiles + spb.boost + (sp.attribute == null ? 0 : sp.attribute.env()), 0);
                    float maxPump = sp.pumpAmount * fraction;
                    liquidProduction(sp.result, maxPump);
                } else if (t.block() instanceof Pump pump) {
                    float pumpSpeed = 0;
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
                            pumpSpeed = pump.pumpAmount * tiles;
                        }
                    } else if (t.floor().liquidDrop != null) {
                        liquid = t.floor().liquidDrop;
                        pumpSpeed = pump.pumpAmount;
                    }

                    if (liquid != null && pumpSpeed > 0) {
                        liquidProduction(liquid, pumpSpeed);
                    }
                }
                //power
                if (t.block() instanceof ItemLiquidGenerator ilg) {
                    if (ilg.consumes.has(ConsumeType.item) && ilg.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        itemConsumption(ci.items, ilg.itemDuration);
                    }
                } else if (t.block() instanceof NuclearReactor nr) {
                    if (nr.consumes.has(ConsumeType.item) && nr.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        itemConsumption(ci.items, nr.itemDuration);
                    }
                } else if (t.block() instanceof ImpactReactor ir) {
                    if (ir.consumes.has(ConsumeType.item) && ir.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        itemConsumption(ci.items, ir.itemDuration);
                    }
                }
                if (t.block() instanceof PowerGenerator pg) {
                    powerProduction(pg.powerProduction);
                }
                //crafting
                if (t.block() instanceof GenericCrafter gc) {
                    if (gc.outputItems != null) {
                        itemProduction(gc.outputItems, gc.craftTime);
                    }
                    if (gc.outputLiquid != null) {
                        if (t.block() instanceof LiquidConverter) {
                            liquidProduction(gc.outputLiquid.liquid, gc.outputLiquid.amount);
                        } else {
                            liquidProduction(gc.outputLiquid.liquid, gc.outputLiquid.amount / gc.craftTime);
                        }
                    }
                    if (gc.consumes.has(ConsumeType.item) && gc.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        itemConsumption(ci.items, gc.craftTime);
                    }
                } if (t.block() instanceof Separator separator) {
                    int totalSlots = 0; //how many slots for random selection
                    HashMap<Item, Integer> chances = new HashMap<>(); //how many slots a item has
                    for (ItemStack is : separator.results) {
                        totalSlots += is.amount;
                        chances.put(is.item, is.amount);
                    }
                    for (Item item : chances.keySet()) {
                        float selectionChance = (float) chances.get(item) / totalSlots;// if 1.0, it will make x item every crafting cycle, if 0.5 it'll be every other cycle... so on and so forth

                        itemPC.putIfAbsent(item, new ArrayList<>());
                        itemPC.get(item).add(new pcEntry(selectionChance / (separator.craftTime / 60f), 0, false));
                    }
                }
                //misc
                if (t.block() instanceof OverdriveProjector op) {
                    if (op.consumes.has(ConsumeType.item) && op.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        itemConsumption(ci.items, op.useTime, ci.optional);
                    }
                } else if (t.block() instanceof MendProjector mp) {
                    if (mp.consumes.has(ConsumeType.item) && mp.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                        itemConsumption(ci.items, mp.useTime, ci.optional);
                    }
                }
                repeat.add(t.build);
            }
        }

        HashMap<Item, averages> maxItemAverages = new HashMap<>();
        itemPC.forEach((k, v) -> {
            float production = 0f;
            float consumption = 0f;
            float consumptionOptional = 0f;

            for (pcEntry pce : v) {
                if (pce.optional) {
                    consumptionOptional -= pce.consumption;
                } else {
                    float net = pce.production - pce.consumption;
                    if (net < 0) {
                        consumption += net;
                    } else {
                        production += net;
                    }
                }
            }
            maxItemAverages.put(k, new averages(production, consumption, consumptionOptional));
        });
        HashMap<Liquid, averages> maxLiquidAverages = new HashMap<>();
        liquidPC.forEach((k, v) -> {
            float production = 0f;
            float consumption = 0f;
            float consumptionOptional = 0f;

            for (pcEntry pce : v) {
                if (pce.optional) {
                    consumptionOptional -= pce.consumption;
                } else {
                    float net = pce.production - pce.consumption;
                    if (net < 0) {
                        consumption += net;
                    } else {
                        production += net;
                    }
                }
            }
            maxLiquidAverages.put(k, new averages(production, consumption, consumptionOptional));
        });
        float maxPowerAverageP = 0;
        float maxPowerAverageN = 0;
        for (float p : powerPC) {
            if (p > 0) {
                maxPowerAverageP += p;
            } else {
                maxPowerAverageN += p;
            }
        }
                /*
                Menus.label(t.block().name + "\n" + t.block().category.name(), 10, t.x * 8f, t.y * 8f);
                ui.chatfrag.addMessage(t.block().name, null);
                ui.chatfrag.addMessage(t.block().category.name(), null);
                 */
        DecimalFormat df = new DecimalFormat("0000.00");
        StringBuilder builder = new StringBuilder(label);
        for (Item i : maxItemAverages.keySet()) {
            averages ia = maxItemAverages.get(i);
            if (ia != null) {
                builder.append("\n[white]").append(i.emoji()).append("[#").append(i.color).append("]").append(i.name).append(" :");
                if (ia.production + ia.consumption != 0) builder.append(ia.production + ia.consumption < 0 ? " [scarlet]" : " [lime]+").append(df.format(ia.production + ia.consumption));
                if (ia.production > 0 && ia.consumption < 0) builder.append(" [white]= [lime]+").append(df.format(ia.production)).append(" [white]+ [scarlet]").append(df.format(ia.consumption));
                if (ia.consumptionOptional < 0) builder.append(" [lightgray](").append(df.format(ia.consumptionOptional)).append(" Optional)");
            }
        }
        for (Liquid l : maxLiquidAverages.keySet()) {
            averages ia = maxLiquidAverages.get(l);
            if (ia != null) {
                builder.append("\n[white]").append(l.emoji()).append("[#").append(l.color).append("]").append(l.name).append(" :");
                if (ia.production + ia.consumption != 0) builder.append(ia.production + ia.consumption < 0 ? " [scarlet]" : " [lime]+").append(df.format(ia.production + ia.consumption));
                if (ia.production > 0 && ia.consumption < 0) builder.append(" [white]= [lime]+").append(df.format(ia.production)).append(" [white]+ [scarlet]").append(df.format(ia.consumption));
                if (ia.consumptionOptional < 0) builder.append(" [lightgray](").append(df.format(ia.consumptionOptional)).append(" Optional)");
            }
        }
        if (maxPowerAverageP > 0 || maxPowerAverageN < 0) {
            builder.append("\n[yellow]Power [white]: ").append(maxPowerAverageP + maxPowerAverageN < 0 ? "[scarlet]" : "[lime]+").append(df.format(maxPowerAverageP + maxPowerAverageN));
            if (maxPowerAverageP > 0) builder.append(" [white]= [lime]+").append(df.format(maxPowerAverageP));
            if (maxPowerAverageN < 0) builder.append(" [white]").append(maxPowerAverageP > 0 ? "+ " : "= ").append("[scarlet]").append(df.format(maxPowerAverageN));
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

    private static class averages {
        public final float production;
        public final float consumption;
        public final float consumptionOptional;

        public averages(float production, float consumption, float consumptionOptional) {
            this.production = production;
            this.consumption = consumption;
            this.consumptionOptional = consumptionOptional;
        }
    }

    private void itemProduction(ItemStack[] stacks, float interval) {
        for (ItemStack is : stacks) {
            itemPC.putIfAbsent(is.item, new ArrayList<>());
            itemPC.get(is.item).add(new pcEntry(is.amount / (interval / 60f), 0, false));
        }
    }

    private void powerProduction(float amount) {
        powerPC.add(amount * 60f);
    }

    private void liquidProduction(Liquid l, float amount) {
        liquidPC.putIfAbsent(l, new ArrayList<>());
        liquidPC.get(l).add(new pcEntry(amount * 60f, 0, false));
    }

    private void itemConsumption(ItemStack[] stacks, float interval) {
        itemConsumption(stacks, interval, false);
    }

    private void itemConsumption(ItemStack[] stacks, float interval, boolean optional) {
        for (ItemStack is : stacks) {
            itemPC.putIfAbsent(is.item, new ArrayList<>());
            itemPC.get(is.item).add(new pcEntry(0, is.amount / (interval / 60f), optional));
        }
    }

    private void powerConsumption(float amount) {
        powerPC.add(-amount * 60f);
    }

    private void liquidConsumption(Liquid l, float amount) {
        liquidConsumption(l, amount, false);
    }

    private void liquidConsumption(Liquid l, float amount, boolean optional) {
        liquidPC.putIfAbsent(l, new ArrayList<>());
        liquidPC.get(l).add(new pcEntry(0, amount * 60f, optional));
    }
}
