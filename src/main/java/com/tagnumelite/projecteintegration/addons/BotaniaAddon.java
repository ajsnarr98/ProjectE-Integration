/*
 * Copyright (c) 2019-2022 TagnumElite
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tagnumelite.projecteintegration.addons;

import com.tagnumelite.projecteintegration.PEIntegration;
import com.tagnumelite.projecteintegration.api.Utils;
import com.tagnumelite.projecteintegration.api.conversion.AConversionProvider;
import com.tagnumelite.projecteintegration.api.conversion.ConversionProvider;
import com.tagnumelite.projecteintegration.api.recipe.ARecipeTypeMapper;
import com.tagnumelite.projecteintegration.api.recipe.nss.NSSInput;
import com.tagnumelite.projecteintegration.api.recipe.nss.NSSOutput;
import moze_intel.projecte.api.ProjectEAPI;
import moze_intel.projecte.api.data.CustomConversionBuilder;
import moze_intel.projecte.api.imc.IMCMethods;
import moze_intel.projecte.api.imc.NSSCreatorInfo;
import moze_intel.projecte.api.mapper.recipe.RecipeTypeMapper;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import moze_intel.projecte.emc.IngredientMap;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import vazkii.botania.api.recipe.*;
import vazkii.botania.common.crafting.ModRecipeTypes;
import vazkii.botania.common.item.ModItems;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// TODO: Look at brew recipes
public class BotaniaAddon {

    /**
     * Register mana as a thing that can use emc.
     * @param modEventBus
     */
    public static void registerMana(IEventBus modEventBus) {
        modEventBus.addListener(BotaniaAddon::imcQueue);
    }

    public static final int EMC_PER_1_000_000_RAW_MANA = 148_308;

    /** Make sure to divide raw mana by this amount before reporting to projectE. **/
//    public static final int MANA_UNIT_TO_REPORT = 1_000_000 / Fractions.GCD(1_000_000, EMC_PER_1_000_000_RAW_MANA);

    /** Make sure to divide raw mana by this amount before reporting to projectE. **/
    public static final double MANA_UNIT_TO_REPORT = 1_000_000.0 / EMC_PER_1_000_000_RAW_MANA;
//    public static final int EMC_PER_SMALLEST_MANA_UNIT = EMC_PER_1_000_000_RAW_MANA / Fractions.GCD(1_000_000, EMC_PER_1_000_000_RAW_MANA);
    public static final int EMC_PER_SMALLEST_MANA_UNIT = 1;

    public static final String MODID = "botania";

    static String NAME(String name) {
        return "Botania" + name + "Mapper";
    }

    private static void imcQueue(InterModEnqueueEvent event) {
        // register mana
        InterModComms.sendTo(ProjectEAPI.PROJECTE_MODID, IMCMethods.REGISTER_NSS_SERIALIZER, () -> new NSSCreatorInfo(NSSMana.KEY, manaName -> NSSMana.INSTANCE));
    }

    private static class NSSMana implements NormalizedSimpleStack {

        public static String KEY = BotaniaAddon.MODID.toUpperCase() + "_MANA";

        public static NSSMana INSTANCE = new NSSMana();

        @Override
        public String json() {
            return KEY;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof NSSMana;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            return json();
        }
    }

    private static abstract class ABotaniaRecipeTypeMapper<R extends Recipe<?>> extends ARecipeTypeMapper<R> {
        /**
         * @return the amount of mana used in the given recipe
         */
        abstract int getRawManaInput(R recipe);

        @Override
        public NSSInput getInput(R recipe) {
            long manaInput = Math.round(getRawManaInput(recipe) / MANA_UNIT_TO_REPORT);
            if (manaInput <= 0) {
                return super.getInput(recipe);
            }

            List<Ingredient> ingredients = getIngredients(recipe);
            if (ingredients == null || ingredients.isEmpty()) {
                PEIntegration.debugLog("Recipe ({}) contains no inputs: {}", recipeID, ingredients);
                return null;
            }

            // A 'Map' of NormalizedSimpleStack and List<IngredientMap>
            List<Tuple<NormalizedSimpleStack, List<IngredientMap<NormalizedSimpleStack>>>> fakeGroupMap = new ArrayList<>();
            IngredientMap<NormalizedSimpleStack> ingredientMap = new IngredientMap<>();

            for (Ingredient ingredient : ingredients) {
                if (!convertIngredient(ingredient, ingredientMap, fakeGroupMap)) {
                    return new NSSInput(ingredientMap, fakeGroupMap, false);
                }
            }
            ingredientMap.addIngredient(NSSMana.INSTANCE, (int)manaInput);
            return new NSSInput(ingredientMap, fakeGroupMap, true);
        }
    }

    @RecipeTypeMapper(requiredMods = MODID, priority = 1)
    public static class BElvenTradeMapper extends ABotaniaRecipeTypeMapper<IElvenTradeRecipe> {
        @Override
        public String getName() {
            return NAME("ElvenTrade");
        }

        @Override
        int getRawManaInput(IElvenTradeRecipe recipe) {
            return 0;
        }

        @Override
        public boolean canHandle(RecipeType<?> recipeType) {
            return recipeType == ModRecipeTypes.ELVEN_TRADE_TYPE;
        }

        @Override
        public NSSOutput getOutput(IElvenTradeRecipe recipe) {
            return mapOutputs(recipe.getOutputs().toArray());
        }
    }

    @RecipeTypeMapper(requiredMods = MODID, priority = 1)
    public static class BManaInfusionMapper extends ABotaniaRecipeTypeMapper<IManaInfusionRecipe> {

        @Override
        public String getName() {
            return NAME("ManaInfusion");
        }

        @Override
        int getRawManaInput(IManaInfusionRecipe recipe) {
            return recipe.getManaToConsume();
        }

        @Override
        public boolean canHandle(RecipeType<?> recipeType) {
            return recipeType == ModRecipeTypes.MANA_INFUSION_TYPE;
        }
    }

    @RecipeTypeMapper(requiredMods = MODID, priority = 1)
    public static class BPetalMapper extends ABotaniaRecipeTypeMapper<IPetalRecipe> {
        @Override
        public String getName() {
            return NAME("Petal");
        }

        @Override
        int getRawManaInput(IPetalRecipe recipe) {
            return 0;
        }

        @Override
        public boolean canHandle(RecipeType<?> recipeType) {
            return recipeType == ModRecipeTypes.PETAL_TYPE;
        }
    }

    @RecipeTypeMapper(requiredMods = MODID, priority = 1)
    public static class BPureDaisyMapper extends ABotaniaRecipeTypeMapper<IPureDaisyRecipe> {
        @Override
        public String getName() {
            return NAME("PureDaisy");
        }

        @Override
        int getRawManaInput(IPureDaisyRecipe recipe) {
            return 0;
        }

        @Override
        public boolean canHandle(RecipeType<?> recipeType) {
            return recipeType == ModRecipeTypes.PURE_DAISY_TYPE;
        }

        @Override
        public NSSOutput getOutput(IPureDaisyRecipe recipe) {
            return new NSSOutput(recipe.getOutputState());
        }

        @Override
        public NSSInput getInput(IPureDaisyRecipe recipe) {
            List<BlockState> matches = recipe.getInput().getDisplayed();
            IngredientMap<NormalizedSimpleStack> ingredientMap = new IngredientMap<>();
            List<Tuple<NormalizedSimpleStack, List<IngredientMap<NormalizedSimpleStack>>>> fakeGroupMap = new ArrayList<>();
            boolean res;
            if (matches == null) {
                return null;
            } else if (matches.size() == 1) {
                //Handle this ingredient as a direct representation of the stack it represents
                res = Utils.addBlockToIngredientMap(ingredientMap, matches.get(0).getBlock());
                return new NSSInput(ingredientMap, fakeGroupMap, res);
            } else if (matches.size() > 1) {
                Set<NormalizedSimpleStack> rawNSSMatches = new HashSet<>();
                List<Block> stacks = new ArrayList<>();

                for (BlockState match : matches) {
                    NormalizedSimpleStack nss = Utils.getNSSFromBlock(match.getBlock());
                    if (nss != null)
                        rawNSSMatches.add(nss);
                    stacks.add(match.getBlock());
                }

                int count = stacks.size();
                if (count == 1) {// I feel like this is unreachable code.... TODO: Unreachable Code?
                    res = Utils.addBlockToIngredientMap(ingredientMap, stacks.get(0));
                    return new NSSInput(ingredientMap, fakeGroupMap, res);
                } else {
                    //Handle this ingredient as the representation of all the stacks it supports
                    Tuple<NormalizedSimpleStack, Boolean> group = fakeGroupManager.getOrCreateFakeGroup(rawNSSMatches);
                    NormalizedSimpleStack dummy = group.getA();
                    ingredientMap.addIngredient(dummy, 1);
                    if (group.getB()) {
                        //Only lookup the matching stacks for the group with conversion if we don't already have
                        // a group created for this dummy ingredient
                        // Note: We soft ignore cases where it fails/there are no matching group ingredients
                        // as then our fake ingredient will never actually have an emc value assigned with it
                        // so the recipe won't either
                        List<IngredientMap<NormalizedSimpleStack>> groupIngredientMaps = new ArrayList<>();
                        for (Block block : stacks) {
                            IngredientMap<NormalizedSimpleStack> groupIngredientMap = new IngredientMap<>();
                            if (!Utils.addBlockToIngredientMap(groupIngredientMap, block))
                                continue;
                            groupIngredientMaps.add(groupIngredientMap);
                        }
                        fakeGroupMap.add(new Tuple<>(dummy, groupIngredientMaps));
                    }
                }
            }
            return new NSSInput(ingredientMap, fakeGroupMap, true);
        }
    }

    @RecipeTypeMapper(requiredMods = MODID, priority = 1)
    public static class BRuneAlterMapper extends ABotaniaRecipeTypeMapper<IRuneAltarRecipe> {
        @Override
        public String getName() {
            return NAME("RuneAlter");
        }

        @Override
        int getRawManaInput(IRuneAltarRecipe recipe) {
            return recipe.getManaUsage();
        }

        @Override
        public boolean canHandle(RecipeType<?> recipeType) {
            return recipeType == ModRecipeTypes.RUNE_TYPE;
        }
    }

    @RecipeTypeMapper(requiredMods = MODID, priority = 1)
    public static class BTerraPlateMapper extends ABotaniaRecipeTypeMapper<ITerraPlateRecipe> {
        @Override
        public String getName() {
            return NAME("TerraPlate");
        }

        @Override
        int getRawManaInput(ITerraPlateRecipe recipe) {
            return recipe.getMana();
        }

        @Override
        public boolean canHandle(RecipeType<?> recipeType) {
            return recipeType == ModRecipeTypes.TERRA_PLATE_TYPE;
        }
    }

    @ConversionProvider(MODID)
    public static class BotaniaConversionProvider extends AConversionProvider {
        @Override
        public void convert(CustomConversionBuilder builder) {
            builder.comment("Default conversions for Botania")
                    .before(ModItems.pebble, 1)
                    .before(ModItems.livingroot, 1)
                    .before(ModItems.lifeEssence, 256)
                    .before(ModItems.enderAirBottle, 1024)
                    .before(NSSMana.INSTANCE, EMC_PER_SMALLEST_MANA_UNIT);
        }
    }
}
