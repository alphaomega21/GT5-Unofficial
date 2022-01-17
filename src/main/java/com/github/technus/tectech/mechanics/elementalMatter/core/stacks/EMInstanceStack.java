package com.github.technus.tectech.mechanics.elementalMatter.core.stacks;

import com.github.technus.tectech.TecTech;
import com.github.technus.tectech.mechanics.elementalMatter.core.decay.EMDecay;
import com.github.technus.tectech.mechanics.elementalMatter.core.decay.EMDecayResult;
import com.github.technus.tectech.mechanics.elementalMatter.core.maps.EMConstantStackMap;
import com.github.technus.tectech.mechanics.elementalMatter.core.maps.EMInstanceStackMap;
import com.github.technus.tectech.mechanics.elementalMatter.core.templates.EMComplex;
import com.github.technus.tectech.mechanics.elementalMatter.core.templates.IEMDefinition;
import com.github.technus.tectech.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.crash.CrashReport;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;

import static com.github.technus.tectech.mechanics.elementalMatter.core.transformations.EMTransformationInfo.AVOGADRO_CONSTANT;
import static com.github.technus.tectech.mechanics.elementalMatter.definitions.primitive.EMPrimitiveDefinition.null__;
import static com.github.technus.tectech.mechanics.elementalMatter.definitions.primitive.EMBosonDefinition.deadEnd;
import static com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_EM_scanner.*;
import static com.github.technus.tectech.util.DoubleCount.*;
import static java.lang.Math.*;

/**
 * Created by danie_000 on 22.10.2016.
 */
public final class EMInstanceStack implements IEMStack {
    public static int MIN_MULTIPLE_DECAY_CALLS = 4, MAX_MULTIPLE_DECAY_CALLS = 16;
    public static double DECAY_CALL_PER = AVOGADRO_CONSTANT;//todo

    private final IEMDefinition definition;
    private       double        amount;

    private double age;
    //energy - if positive then particle should try to decay
    private long   energy;
    //byte color; 0=Red 1=Green 2=Blue 0=Cyan 1=Magenta 2=Yellow, else ignored (-1 - uncolorable)
    private byte   color;
    private double lifeTime;
    private double lifeTimeMult;

    public EMInstanceStack(IEMStack stackSafe) {
        this(stackSafe.getDefinition(), stackSafe.getAmount(), 1D, 0D, 0);
    }

    public EMInstanceStack(IEMStack stackSafe, double lifeTimeMult, double age, long energy) {
        this(stackSafe.getDefinition(), stackSafe.getAmount(), lifeTimeMult, age, energy);
    }

    public EMInstanceStack(IEMDefinition defSafe, double amount) {
        this(defSafe, amount, 1D, 0D, 0);
    }

    public EMInstanceStack(IEMDefinition defSafe, double amount, double lifeTimeMult, double age, long energy) {
        definition = defSafe == null ? null__ : defSafe;
        byte bColor = getDefinition().getColor();
        if (bColor < 0 || bColor > 2) {//transforms colorable??? into proper color
            this.color = bColor;
        } else {
            this.color = (byte) TecTech.RANDOM.nextInt(3);
        }
        this.lifeTimeMult = lifeTimeMult;
        lifeTime = getDefinition().getRawTimeSpan(energy) * this.lifeTimeMult;
        setEnergy(energy);
        this.setAge(age);
        this.setAmount(amount);
    }

    //Clone proxy
    private EMInstanceStack(EMInstanceStack stack) {
        definition = stack.getDefinition();
        color = stack.color;
        setAge(stack.getAge());
        setAmount(stack.getAmount());
        lifeTime = stack.lifeTime;
        lifeTimeMult = stack.lifeTimeMult;
        energy = stack.energy;
    }

    @Override
    public EMInstanceStack clone() {
        return new EMInstanceStack(this);
    }

    @Override
    public EMInstanceStack mutateAmount(double newAmount) {
        this.setAmount(newAmount);
        return this;
    }

    @Override
    public double getAmount() {
        return amount;
    }

    public long getEnergy() {
        return energy;
    }

    public void setEnergy(long newEnergyLevel) {
        energy = newEnergyLevel;
        setLifeTimeMultiplier(getLifeTimeMultiplier());
    }

    public double getEnergySettingCost(long currentEnergyLevel, long newEnergyLevel) {
        return getDefinition().getEnergyDiffBetweenStates(currentEnergyLevel, newEnergyLevel) * getAmount();
    }

    public double getEnergySettingCost(long newEnergyLevel) {
        return getEnergySettingCost(energy, newEnergyLevel) * getAmount();
    }

    public EMDefinitionStack getDefinitionStack() {
        return new EMDefinitionStack(getDefinition(), getAmount());
    }

    @Override
    public IEMDefinition getDefinition() {
        return definition;
    }

    public byte getColor() {
        return color;
    }

    public void setColor(byte color) {//does not allow changing magic element
        if (this.color < 0 || this.color > 2 || color < 0 || color >= 3) {
            return;
        }
        this.color = color;
    }

    public void nextColor() {//does not allow changing magic element
        if (color < 0 || color > 2) {
            return;
        }
        color = (byte) TecTech.RANDOM.nextInt(3);
    }

    public double getLifeTime() {
        return lifeTime;
    }

    public void setLifeTimeMultiplier(double mult) {
        if (mult <= 0) //since infinity*0=nan
        {
            throw new IllegalArgumentException("multiplier must be >0");
        }
        lifeTimeMult = mult;
        if (getDefinition().getRawTimeSpan(energy) <= 0) {
            return;
        }
        lifeTime = getDefinition().getRawTimeSpan(energy) * lifeTimeMult;
    }

    public double getLifeTimeMultiplier() {
        return lifeTimeMult;
    }

    public EMDecayResult tickStackByOneSecond(double lifeTimeMult, int postEnergize) {
        return tickStack(lifeTimeMult, postEnergize, 1D);
    }

    public EMDecayResult tickStack(double lifeTimeMult, int postEnergize, double seconds) {
        setAge(getAge() + seconds);
        EMDecayResult newInstances = decay(lifeTimeMult, getAge(), postEnergize);
        if (newInstances == null) {
            nextColor();
        } else {
            for (EMInstanceStack newInstance : newInstances.getOutput().valuesToArray()) {
                newInstance.nextColor();
            }
        }
        return newInstances;
    }

    public EMDecayResult decay() {
        return decay(1D, getAge(), 0);//try to decay without changes
    }

    public EMDecayResult decay(double apparentAge, long postEnergize) {
        return decay(1D, apparentAge, postEnergize);
    }

    public EMDecayResult decay(double lifeTimeMult, double apparentAge, long postEnergize) {
        long newEnergyLevel = postEnergize + energy;
        if (newEnergyLevel > 0) {
            newEnergyLevel -= 1;
        } else if (newEnergyLevel < 0) {
            newEnergyLevel += 1;
        }
        EMDecayResult output;
        if (getDefinition().usesMultipleDecayCalls(energy)) {
            double amountTemp = getAmount();
            int    decayCnt   = (int) min(MAX_MULTIPLE_DECAY_CALLS, max(getAmount() / DECAY_CALL_PER, MIN_MULTIPLE_DECAY_CALLS));
            setAmount(div(getAmount(), decayCnt));
            decayCnt--;

            output = decayMechanics(lifeTimeMult, apparentAge, newEnergyLevel);
            if (output == null) {
                setAmount(amountTemp);
                return null;
            }

            for (int i = 0; i < decayCnt; i++) {
                EMDecayResult map = decayMechanics(lifeTimeMult, apparentAge, newEnergyLevel);
                if (map != null) {
                    output.getOutput().putUnifyAll(map.getOutput());
                    output.setMassDiff(add(output.getMassDiff(), map.getMassDiff()));
                    output.setMassAffected(output.getMassDiff() + map.getMassDiff());
                }
            }
            setAmount(amountTemp);
        } else {
            output = decayMechanics(lifeTimeMult, apparentAge, newEnergyLevel);
        }
        if (output != null) {
            output.getOutput().cleanUp();
        }
        return output;
    }

    private EMDecayResult decayMechanics(double lifeTimeMult, double apparentAge, long newEnergyLevel) {
        if (energy > 0 && !getDefinition().usesSpecialEnergeticDecayHandling()) {
            setLifeTimeMultiplier(getLifeTimeMultiplier());
            return decayCompute(getDefinition().getEnergyInducedDecay(energy), lifeTimeMult, -1D, newEnergyLevel);
        } else if (getDefinition().getRawTimeSpan(energy) < 0) {
            return null;//return null, decay cannot be achieved
        } else if (getDefinition().isTimeSpanHalfLife()) {
            return exponentialDecayCompute(energy > 0 ? getDefinition().getEnergyInducedDecay(energy) : getDefinition().getDecayArray(), lifeTimeMult, -1D, newEnergyLevel);
        } else {
            if (1 > lifeTime) {
                return decayCompute(energy > 0 ? getDefinition().getEnergyInducedDecay(energy) : getDefinition().getNaturalDecayInstant(), lifeTimeMult, 0D, newEnergyLevel);
            } else if (apparentAge > lifeTime) {
                return decayCompute(energy > 0 ? getDefinition().getEnergyInducedDecay(energy) : getDefinition().getDecayArray(), lifeTimeMult, 0D, newEnergyLevel);
            }
        }
        return null;//return null since decay cannot be achieved
    }

    //Use to get direct decay output providing correct decay array
    private EMDecayResult exponentialDecayCompute(EMDecay[] decays, double lifeTimeMult, double newProductsAge, long newEnergyLevel) {
        double newAmount = div(getAmount(), Math.pow(2D, 1D/* 1 second */ / lifeTime));

        //if(definition.getSymbol().startsWith("U ")) {
        //    System.out.println("newAmount = " + newAmount);
        //    System.out.println("amountRemaining = " + amountRemaining);
        //    for(cElementalDecay decay:decays){
        //        System.out.println("prob = "+decay.probability);
        //        for(cElementalDefinitionStack stack:decay.outputStacks.values()){
        //            System.out.println("stack = " + stack.getDefinition().getSymbol() + " " + stack.amount);
        //        }
        //    }
        //}
        if (newAmount == getAmount()) {
            newAmount -= ulpSigned(newAmount);
        } else if (newAmount < 1) {
            return decayCompute(decays, lifeTimeMult, newProductsAge, newEnergyLevel);
        }

        //split to non decaying and decaying part
        double amount = this.getAmount();
        this.setAmount(this.getAmount() - newAmount);
        EMDecayResult products = decayCompute(decays, lifeTimeMult, newProductsAge, newEnergyLevel);
        this.setAmount(newAmount);
        if (products != null) {
            products.getOutput().putUnify(clone());
        }
        this.setAmount(amount);
        return products;
    }

    //Use to get direct decay output providing correct decay array
    private EMDecayResult decayCompute(EMDecay[] decays, double lifeTimeMult, double newProductsAge, long newEnergyLevel) {
        if (decays == null) {
            return null;//Can not decay so it won't
        }
        boolean makesEnergy = getDefinition().decayMakesEnergy(energy);
        double  mass        = getMass();
        if (decays.length == 0) {
            return makesEnergy ? null : new EMDecayResult(new EMInstanceStackMap(), mass, 0);
            //provide non null 0 length array for annihilation
        } else if (decays.length == 1) {//only one type of decay :D, doesn't need dead end
            if (decays[0] == deadEnd) {
                return makesEnergy ? null : new EMDecayResult(decays[0].getResults(lifeTimeMult, newProductsAge, newEnergyLevel, getAmount()), mass, 0);
            }
            EMInstanceStackMap output = decays[0].getResults(lifeTimeMult, newProductsAge, newEnergyLevel, getAmount());
            if (newProductsAge < 0) {
                if (output.size() == 1) {
                    if (output.size() == 1 && output.getFirst().getDefinition().equals(getDefinition())) {
                        output.getFirst().setEnergy(energy);
                        output.getFirst().setAge(getAge());
                    }
                } else {
                    for (EMInstanceStack stack : output.valuesToArray()) {
                        if (stack.getDefinition().equals(getDefinition())) {
                            stack.setAge(getAge());
                        }
                    }
                }
            } else {
                if (output.size() == 1 && output.getFirst().getDefinition().equals(getDefinition())) {
                    output.getFirst().setEnergy(energy);
                }
            }
            if (energy <= 0 && output.getMass() > mass) {
                return null;//no energy usage to decay
            }
            return new EMDecayResult(new EMInstanceStackMap(), mass, makesEnergy ? output.getMass() - mass : 0);
        } else {
            EMDecayResult      totalOutput     = new EMDecayResult(new EMInstanceStackMap(), getMass(), 0);
            EMInstanceStackMap output          = totalOutput.getOutput(), results;
            int                differentDecays = decays.length;
            double[]           probabilities   = new double[differentDecays];
            for (int i = 0; i < probabilities.length; i++) {
                probabilities[i] = decays[i].getProbability();
            }
            double[] qttyOfDecay;
            try {
                qttyOfDecay = distribute(getAmount(), probabilities);
            } catch (ArithmeticException e) {
                Minecraft.getMinecraft().crashed(new CrashReport("Decay failed for: " + this, e));
                return null;
            }
            //long amountRemaining = this.amount, amount = this.amount;
            //float remainingProbability = 1D;
//
            //for (int i = 0; i < differentDecays; i++) {
            //    if (decays[i].probability >= 1D) {
            //        long thisDecayAmount = (long) Math.floor(remainingProbability * (double) amount);
            //        if (thisDecayAmount > 0) {
            //            if (thisDecayAmount <= amountRemaining) {
            //                amountRemaining -= thisDecayAmount;
            //                qttyOfDecay[i] += thisDecayAmount;
            //            }else {//in case too much was made
            //                qttyOfDecay[i] += amountRemaining;
            //                amountRemaining = 0;
            //                //remainingProbability=0;
            //            }
            //        }
            //        break;
            //    }
            //    long thisDecayAmount = (long) Math.floor(decays[i].probability * (double) amount);
            //    if (thisDecayAmount <= amountRemaining && thisDecayAmount > 0) {//some was made
            //        amountRemaining -= thisDecayAmount;
            //        qttyOfDecay[i] += thisDecayAmount;
            //    } else if (thisDecayAmount > amountRemaining) {//too much was made
            //        qttyOfDecay[i] += amountRemaining;
            //        amountRemaining = 0;
            //        //remainingProbability=0;
            //        break;
            //    }
            //    remainingProbability -= decays[i].probability;
            //    if(remainingProbability<=0) {
            //        break;
            //    }
            //}

            //for (int i = 0; i < amountRemaining; i++) {
            //    double rand = TecTech.RANDOM.nextDouble();
            //    for (int j = 0; j < differentDecays; j++) {//looking for the thing it decayed into
            //        rand -= decays[j].probability;
            //        if (rand <= 0D) {
            //            qttyOfDecay[j]++;
            //            break;
            //        }
            //    }
            //}

            if (getDefinition().decayMakesEnergy(energy)) {
                for (int i = differentDecays - 1; i >= 0; i--) {
                    if (decays[i] == deadEnd) {
                        EMInstanceStack clone = clone();
                        clone.setAmount(qttyOfDecay[i]);
                        output.putUnify(clone);
                    } else {
                        results = decays[i].getResults(lifeTimeMult, newProductsAge, newEnergyLevel, qttyOfDecay[i]);
                        output.putUnifyAll(results);
                        totalOutput.setMassDiff(add(totalOutput.getMassDiff(), results.getMass() - mass));
                    }
                }
            } else {
                for (int i = differentDecays - 1; i >= 0; i--) {
                    results = decays[i].getResults(lifeTimeMult, newProductsAge, newEnergyLevel, qttyOfDecay[i]);
                    output.putUnifyAll(results);
                }
            }

            if (newProductsAge < 0) {
                if (output.size() == 1 && output.getFirst().getDefinition().equals(getDefinition())) {
                    output.getFirst().setEnergy(energy);
                    output.getFirst().setAge(getAge());
                } else {
                    for (EMInstanceStack stack : output.valuesToArray()) {
                        if (stack.getDefinition().equals(getDefinition())) {
                            stack.setAge(getAge());
                        }
                    }
                }
            } else {
                if (output.size() == 1 && output.getFirst().getDefinition().equals(getDefinition())) {
                    output.getFirst().setEnergy(energy);
                    output.getFirst().setAge(getAge());
                }
            }
            if (energy <= 0 && output.getMass() > getMass()) {
                return null;//no energy usage to decay
            }
            return totalOutput;
        }
    }

    public EMInstanceStack unifyIntoThis(EMInstanceStack... instances) {
        if (instances == null) {
            return this;
        }
        //returns with the definition from the first object passed
        double energyTotal = getEnergySettingCost(0, energy);
        long   maxEnergy   = energy;
        long   minEnergy   = energy;

        for (EMInstanceStack instance : instances) {
            //if (instance != null && compareTo(instance) == 0) {
            setAmount(add(getAmount(), instance.getAmount()));
            energyTotal += instance.getEnergySettingCost(0, instance.energy);
            maxEnergy = max(instance.energy, maxEnergy);
            minEnergy = min(instance.energy, maxEnergy);
            lifeTimeMult = min(lifeTimeMult, instance.lifeTimeMult);
            setAge(max(getAge(), instance.getAge()));
            //}
        }

        if (energyTotal >= 0) {
            for (; maxEnergy > 0; maxEnergy--) {
                if (getEnergySettingCost(0, maxEnergy) < energyTotal) {
                    break;
                }
            }
            setEnergy(maxEnergy);
        } else {
            for (; minEnergy < 0; minEnergy++) {
                if (getEnergySettingCost(minEnergy, 0) < energyTotal) {
                    break;
                }
            }
            setEnergy(minEnergy);
        }
        return this;
    }

    public EMInstanceStack unifyIntoThisExact(EMInstanceStack... instances) {
        if (instances == null) {
            return this;
        }
        //returns with the definition from the first object passed
        double energyTotal = getEnergySettingCost(0, energy);
        long   maxEnergy   = energy;
        long   minEnergy   = energy;

        for (EMInstanceStack instance : instances) {
            //if (instance != null && compareTo(instance) == 0) {
            setAmount(getAmount() + instance.getAmount());
            energyTotal += instance.getEnergySettingCost(0, instance.energy);
            maxEnergy = max(instance.energy, maxEnergy);
            minEnergy = min(instance.energy, maxEnergy);
            lifeTimeMult = min(lifeTimeMult, instance.lifeTimeMult);
            setAge(max(getAge(), instance.getAge()));
            //}
        }

        if (energyTotal >= 0) {
            for (; maxEnergy > 0; maxEnergy--) {
                if (getEnergySettingCost(0, maxEnergy) < energyTotal) {
                    break;
                }
            }
            setEnergy(maxEnergy);
        } else {
            for (; minEnergy < 0; minEnergy++) {
                if (getEnergySettingCost(minEnergy, 0) < energyTotal) {
                    break;
                }
            }
            setEnergy(minEnergy);
        }
        return this;
    }

    public void addScanShortSymbols(ArrayList<String> lines, int[] detailsOnDepthLevels) {
        int capabilities = detailsOnDepthLevels[0];
        getDefinition().addScanShortSymbols(lines, capabilities, energy);
        //scanShortSymbolsContents(lines,definition.getSubParticles(),1,detailsOnDepthLevels);
    }

    //private void scanShortSymbolsContents(ArrayList<String> lines, cElementalDefinitionStackMap definitions, int depth, int[] detailsOnDepthLevels){
    //    if(definitions!=null && depth<detailsOnDepthLevels.length){
    //        int deeper=depth+1;
    //        for(cElementalDefinitionStack definitionStack:definitions.values()) {
    //            definition.addScanShortSymbols(lines,detailsOnDepthLevels[depth],energy);
    //            scanSymbolsContents(lines,definitionStack.definition.getSubParticles(),deeper,detailsOnDepthLevels);
    //        }
    //    }
    //}

    public void addScanResults(ArrayList<String> lines, int[] detailsOnDepthLevels) {
        int capabilities = detailsOnDepthLevels[0];
        if (Util.areBitsSet(SCAN_GET_DEPTH_LEVEL, capabilities)) {
            lines.add("DEPTH = " + 0);
        }
        getDefinition().addScanResults(lines, capabilities, energy);
        if (Util.areBitsSet(SCAN_GET_TIMESPAN_MULT, capabilities)) {
            lines.add("TIME MULT = " + lifeTimeMult);
            if (Util.areBitsSet(SCAN_GET_TIMESPAN_INFO, capabilities)) {
                lines.add("TIME SPAN = " + lifeTime + " s");
            }
        }
        if (Util.areBitsSet(SCAN_GET_AGE, capabilities)) {
            lines.add("AGE = " + getAge() + " s");
        }
        if (Util.areBitsSet(SCAN_GET_COLOR, capabilities)) {
            lines.add("COLOR = " + color + " RGB or CMY");
        }
        if (Util.areBitsSet(SCAN_GET_ENERGY_LEVEL, capabilities)) {
            lines.add("ENERGY = " + energy);
        }
        if (Util.areBitsSet(SCAN_GET_AMOUNT, capabilities)) {
            lines.add("AMOUNT = " + getAmount() / AVOGADRO_CONSTANT + " mol");
        }
        scanContents(lines, getDefinition().getSubParticles(), 1, detailsOnDepthLevels);
    }

    private void scanContents(ArrayList<String> lines, EMConstantStackMap definitions, int depth, int[] detailsOnDepthLevels) {
        if (definitions != null && depth < detailsOnDepthLevels.length) {
            int deeper = depth + 1;
            for (EMDefinitionStack definitionStack : definitions.valuesToArray()) {
                lines.add("");//def separator
                if (Util.areBitsSet(SCAN_GET_DEPTH_LEVEL, detailsOnDepthLevels[depth])) {
                    lines.add("DEPTH = " + depth);
                }
                getDefinition().addScanResults(lines, detailsOnDepthLevels[depth], energy);
                if (Util.areBitsSet(SCAN_GET_AMOUNT, detailsOnDepthLevels[depth])) {
                    lines.add("AMOUNT = " + definitionStack.getAmount());
                }
                scanContents(lines, definitionStack.getDefinition().getSubParticles(), deeper, detailsOnDepthLevels);
            }
        }
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("d", getDefinition().toNBT());
        nbt.setDouble("Q", getAmount());
        nbt.setLong("e", energy);
        nbt.setByte("c", color);
        nbt.setDouble("A", getAge());
        nbt.setDouble("M", lifeTimeMult);
        return nbt;
    }

    public static EMInstanceStack fromNBT(NBTTagCompound nbt) {
        NBTTagCompound definition = nbt.getCompoundTag("d");
        EMInstanceStack instance = new EMInstanceStack(
                EMComplex.fromNBT(definition),
                nbt.getLong("q") + nbt.getDouble("Q"),
                nbt.getFloat("m") + nbt.getDouble("M"),
                nbt.getLong("a") + nbt.getDouble("A"),
                nbt.getLong("e"));
        instance.setColor(nbt.getByte("c"));
        return instance;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IEMDefinition) {
            return getDefinition().compareTo((IEMDefinition) obj) == 0;
        }
        if (obj instanceof IEMStack) {
            return getDefinition().compareTo(((IEMStack) obj).getDefinition()) == 0;
        }
        return false;
    }

    //Amount shouldn't be hashed if this is just indicating amount and not structure, DOES NOT CARE ABOUT creativeTabTecTech INFO
    @Override
    public int hashCode() {
        return getDefinition().hashCode();
    }

    @Override
    public String toString() {
        return getDefinition().toString() + '\n' + getAmount() / AVOGADRO_CONSTANT + " mol\n" + getMass();
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public double getAge() {
        return age;
    }

    public void setAge(double age) {
        this.age = age;
    }
}
