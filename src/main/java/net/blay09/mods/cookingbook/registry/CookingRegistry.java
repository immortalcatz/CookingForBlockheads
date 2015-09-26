package net.blay09.mods.cookingbook.registry;

import com.google.common.collect.ArrayListMultimap;
import net.blay09.mods.cookingbook.addon.HarvestCraftAddon;
import net.blay09.mods.cookingbook.api.SinkHandler;
import net.blay09.mods.cookingbook.api.event.FoodRegistryInitEvent;
import net.blay09.mods.cookingbook.api.kitchen.IKitchenItemProvider;
import net.blay09.mods.cookingbook.container.InventoryCraftBook;
import net.blay09.mods.cookingbook.registry.food.FoodIngredient;
import net.blay09.mods.cookingbook.registry.food.FoodRecipe;
import net.blay09.mods.cookingbook.registry.food.recipe.*;
import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import java.util.*;

public class CookingRegistry {

    private static final List<IRecipe> recipeList = new ArrayList<>();
    private static final ArrayListMultimap<Item, FoodRecipe> foodItems = ArrayListMultimap.create();
    private static final List<ItemStack> tools = new ArrayList<>();
    private static final Map<ItemStack, Integer> ovenFuelItems = new HashMap<>();
    private static final Map<ItemStack, ItemStack> ovenRecipes = new HashMap<>();
    private static final Map<ItemStack, SinkHandler> sinkHandlers = new HashMap<>();

    public static void initFoodRegistry() {
        recipeList.clear();
        foodItems.clear();

        FoodRegistryInitEvent init = new FoodRegistryInitEvent();
        MinecraftForge.EVENT_BUS.post(init);

        Collection<ItemStack> nonFoodRecipes = init.getNonFoodRecipes();

        // Crafting Recipes of Food Items
        for(Object obj : CraftingManager.getInstance().getRecipeList()) {
            IRecipe recipe = (IRecipe) obj;
            ItemStack output = recipe.getRecipeOutput();
            if(output != null) {
                if (output.getItem() instanceof ItemFood) {
                    if (HarvestCraftAddon.isWeirdBrokenRecipe(recipe)) {
                        continue;
                    }
                    addFoodRecipe(recipe);
                } else {
                    for (ItemStack itemStack : nonFoodRecipes) {
                        if (areItemStacksEqualWithWildcard(recipe.getRecipeOutput(), itemStack)) {
                            addFoodRecipe(recipe);
                            break;
                        }
                    }
                }
            }
        }

        // Smelting Recipes of Food Items
        for(Object obj : FurnaceRecipes.smelting().getSmeltingList().entrySet()) {
            Map.Entry entry = (Map.Entry) obj;
            ItemStack sourceStack = null;
            if(entry.getKey() instanceof Item) {
                sourceStack = new ItemStack((Item) entry.getKey());
            } else if(entry.getKey() instanceof Block) {
                sourceStack = new ItemStack((Block) entry.getKey());
            } else if(entry.getKey() instanceof ItemStack) {
                sourceStack = (ItemStack) entry.getKey();
            }
            ItemStack resultStack = (ItemStack) entry.getValue();
            if(resultStack.getItem() instanceof ItemFood) {
                foodItems.put(resultStack.getItem(), new SmeltingFood(resultStack, sourceStack));
            } else {
                for(ItemStack itemStack : nonFoodRecipes) {
                    if (areItemStacksEqualWithWildcard(resultStack, itemStack)) {
                        foodItems.put(resultStack.getItem(), new SmeltingFood(resultStack, sourceStack));
                        break;
                    }
                }
            }
        }
    }

    public static void addFoodRecipe(IRecipe recipe) {
        ItemStack output = recipe.getRecipeOutput();
        if(output != null) {
            recipeList.add(recipe);
            if (recipe instanceof ShapedRecipes) {
                foodItems.put(output.getItem(), new ShapedCraftingFood((ShapedRecipes) recipe));
            } else if (recipe instanceof ShapelessRecipes) {
                foodItems.put(output.getItem(), new ShapelessCraftingFood((ShapelessRecipes) recipe));
            } else if (recipe instanceof ShapelessOreRecipe) {
                foodItems.put(output.getItem(), new ShapelessOreCraftingFood((ShapelessOreRecipe) recipe));
            } else if (recipe instanceof ShapedOreRecipe) {
                foodItems.put(output.getItem(), new ShapedOreCraftingFood((ShapedOreRecipe) recipe));
            }
        }
    }

    public static boolean areIngredientsAvailableFor(List<FoodIngredient> craftMatrix, List<IInventory> inventories, List<IKitchenItemProvider> itemProviders) {
        int[][] usedStackSize = new int[inventories.size()][];
        for(int i = 0; i < usedStackSize.length; i++) {
            usedStackSize[i] = new int[inventories.get(i).getSizeInventory()];
        }
        boolean[] itemFound = new boolean[craftMatrix.size()];
        matrixLoop:for(int i = 0; i < craftMatrix.size(); i++) {
            if(craftMatrix.get(i) == null || craftMatrix.get(i).isToolItem()) {
                itemFound[i] = true;
                continue;
            }
            for(IKitchenItemProvider itemProvider : itemProviders) {
                itemProvider.clearCraftingBuffer();
                for(ItemStack providedStack : itemProvider.getProvidedItemStacks()) {
                    if(craftMatrix.get(i).isValidItem(providedStack)) {
                        if(itemProvider.addToCraftingBuffer(providedStack)) {
                            itemFound[i] = true;
                            continue matrixLoop;
                        }
                    }
                }
            }
            for(int j = 0; j < inventories.size(); j++) {
                for (int k = 0; k < inventories.get(j).getSizeInventory(); k++) {
                    ItemStack itemStack = inventories.get(j).getStackInSlot(k);
                    if (itemStack != null && craftMatrix.get(i).isValidItem(itemStack) && itemStack.stackSize - usedStackSize[j][k] > 0) {
                        usedStackSize[j][k]++;
                        itemFound[i] = true;
                        continue matrixLoop;
                    }
                }
            }
        }
        for(int i = 0; i < itemFound.length; i++) {
            if(!itemFound[i]) {
                return false;
            }
        }
        return true;
    }

    public static IRecipe findMatchingFoodRecipe(InventoryCraftBook craftBook, World worldObj) {
        for(IRecipe recipe : recipeList) {
            if(recipe.matches(craftBook, worldObj)) {
                return recipe;
            }
        }
        return null;
    }

    public static Collection<FoodRecipe> getFoodRecipes() {
        return foodItems.values();
    }

    public static void addToolItem(ItemStack toolItem) {
        tools.add(toolItem);
    }

    public static boolean isToolItem(ItemStack itemStack) {
        if(itemStack == null) {
            return false;
        }
        for(ItemStack toolItem : tools) {
            if(areItemStacksEqualWithWildcard(toolItem, itemStack)) {
                return true;
            }
        }
        return false;
    }

    public static void addOvenFuel(ItemStack itemStack, int fuelTime) {
        ovenFuelItems.put(itemStack, fuelTime);
    }

    public static int getOvenFuelTime(ItemStack itemStack) {
        for(Map.Entry<ItemStack, Integer> entry : ovenFuelItems.entrySet()) {
            if(areItemStacksEqualWithWildcard(entry.getKey(), itemStack)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    public static void addSmeltingItem(ItemStack source, ItemStack result) {
        ovenRecipes.put(source, result);
    }

    public static ItemStack getSmeltingResult(ItemStack itemStack) {
        for(Map.Entry<ItemStack, ItemStack> entry : ovenRecipes.entrySet()) {
            if(areItemStacksEqualWithWildcard(entry.getKey(), itemStack)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static void addSinkHandler(ItemStack itemStack, SinkHandler sinkHandler) {
        sinkHandlers.put(itemStack, sinkHandler);
    }

    public static ItemStack getSinkOutput(ItemStack itemStack) {
        for(Map.Entry<ItemStack, SinkHandler> entry : sinkHandlers.entrySet()) {
            if(areItemStacksEqualWithWildcard(entry.getKey(), itemStack)) {
                return entry.getValue().getSinkOutput(itemStack);
            }
        }
        return null;
    }

    public static boolean areItemStacksEqualWithWildcard(ItemStack first, ItemStack second) {
        if(first == null || second == null) {
            return false;
        }
        return first.getItem() == second.getItem() && (first.getItemDamage() == second.getItemDamage() || first.getItemDamage() == OreDictionary.WILDCARD_VALUE || second.getItemDamage() == OreDictionary.WILDCARD_VALUE);
    }

}
