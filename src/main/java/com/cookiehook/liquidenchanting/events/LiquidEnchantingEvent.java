package com.cookiehook.liquidenchanting.events;

import com.cookiehook.liquidenchanting.util.Config;
import com.cookiehook.liquidenchanting.util.LiquidEnchantmentHelper;
import com.cookiehook.liquidenchanting.util.RomanNumber;
import com.google.common.collect.Lists;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Iterator;
import java.util.List;

public class LiquidEnchantingEvent {

    private ItemStack bow;

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void renderLivingEvent(RenderLivingEvent.Pre event) {
        Entity player = event.getEntity();
        if (event.getEntity() instanceof EntityPlayer) {
            Iterator<ItemStack> armor = player.getArmorInventoryList().iterator();
            List<ItemStack> armorStack = Lists.newArrayList(armor);

            //Loops through each armor slot, cancelling the player render if an invisibility item is worn.
            for (ItemStack itemstack : armorStack) {
                List<PotionEffect> potionEffects = LiquidEnchantmentHelper.getPotionTypeFromNBT(itemstack.getTagCompound());

                for (PotionEffect potionEffect : potionEffects) {
                    if (potionEffect.getEffectName().equals("effect.invisibility")) {
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void ArrowLooseEvent(ArrowLooseEvent event) {
        // Save the bow actually used to fire the arrow. This is used later when the arrow is spawned.
        this.bow = event.getBow();
    }

    @SubscribeEvent
    public void EntityJoinWorldEvent(EntityJoinWorldEvent event) {
        Entity arrow = event.getEntity();
        if (arrow instanceof EntityArrow && this.bow != null && this.bow.getTagCompound() != null) {
            // Save the potion effect from the bow saved earlier onto the arrow entity
            NBTTagCompound tag = arrow.getEntityData();
            tag.merge(this.bow.getTagCompound());
            this.bow = null;
        }
    }

    /*
     * This method is called evey time a living entity (any mob) is hurt.
     * Each time it does, we:
     *      Check that the damage came from a player
     *      Check what the player is currently holding
     *      Apply the potion effects from that item to the entity that was hurt
     */
    @SubscribeEvent
    public void LivingHurtEvent(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        Entity target = event.getEntity();
        List<PotionEffect> potionEffects = null;

        // Entity is being attacked by a projectile
        if (source instanceof EntityDamageSourceIndirect) {
            Entity projectile = source.getImmediateSource();
            if (!(projectile instanceof EntityArrow)) {
                return;
            }
            // Read the NBT data we saved from the bow, then parse to extract PotionEffects
            NBTTagCompound potions = projectile.getEntityData();
            if (potions.getSize() != 0) {
                potionEffects = LiquidEnchantmentHelper.getPotionTypeFromNBT(potions);
            }
        }
        // Entity is being attacked by player throwing hands
        else if (source instanceof EntityDamageSource) {
            Entity attacker = source.getTrueSource();

            // Exit if damage is not player-caused (falling, fire, starvation, magic etc)
            if (!(attacker instanceof EntityPlayer)) {
                return;
            }

            //Don't apply potion effects if we're slapping someone with defensive items. That would be silly
            ItemStack weapon = ((EntityPlayer) attacker).getHeldItemMainhand();
            Item weaponItem = weapon.getItem();
            if (weaponItem instanceof ItemArmor || weaponItem instanceof ItemBow
                    || weaponItem instanceof ItemShield || weaponItem instanceof ItemElytra) {
                return;
            }

            // Check that the item we're hitting with has been crafted using our crafting manager
            if ((weapon.getTagCompound() != null) && (weapon.getTagCompound().getBoolean("liquid_enchanted"))) {
                potionEffects = LiquidEnchantmentHelper.getPotionTypeFromNBT(weapon.getTagCompound());
            }
        }

        if (potionEffects != null) {
            for (PotionEffect potionEffect : potionEffects) {
                Potion potion = potionEffect.getPotion();
                // Apply the effect for 1 tick if it's instant, or the configured weapon time if not.
                int duration = potion.isInstant() ? 1 : Config.weaponEffectTime;
                ((EntityLivingBase) target).addPotionEffect(new PotionEffect(potion, duration, potionEffect.getAmplifier(), false, true));
            }
        }
    }

    /**
     * This method is called every time the player is ticked by the server.
     * Each time it does, we:
     * Get a list of all the armor the player is wearing
     * Get a list of all allowed potions applied to that armor
     * Apply each potion to the player
     */
    @SubscribeEvent
    public void playerTick(TickEvent.PlayerTickEvent event) {
        EntityLivingBase player = event.player;
        Iterator<ItemStack> armor = player.getArmorInventoryList().iterator();
        List<ItemStack> armorStack = Lists.newArrayList(armor);

        //Loops through each armor slot, applying any worn enchantments
        for (ItemStack itemstack : armorStack) {
            List<PotionEffect> potionEffects = LiquidEnchantmentHelper.getPotionTypeFromNBT(itemstack.getTagCompound());

            for (PotionEffect potionEffect : potionEffects) {
                LiquidEnchantmentHelper.addPotionEffect(player, potionEffect.getPotion(), potionEffect.getAmplifier());
            }
        }

        // Check what the player is holding in either hand
        ItemStack mainHand = player.getHeldItemMainhand();
        ItemStack offHand = player.getHeldItemOffhand();
        ItemStack shield = null;

        // Check if either is a shield, save it if so.
        if (mainHand.getItem() instanceof ItemShield) {
            shield = mainHand;
        } else if (offHand.getItem() instanceof ItemShield) {
            shield = offHand;
        }

        // If a shield was being held, apply potion effects to player
        if (shield != null) {
            List<PotionEffect> potionEffects = LiquidEnchantmentHelper.getPotionTypeFromNBT(shield.getTagCompound());

            for (PotionEffect potionEffect : potionEffects) {
                LiquidEnchantmentHelper.addPotionEffect(player, potionEffect.getPotion(), potionEffect.getAmplifier());
            }
        }
    }

    /**
     * This is called every time we mouse over an item in inventories (player, chest, hopper etc..)
     * Each time it does we:
     * Check that the chosen itemstack is the right type
     * Get all allowed potion effects from that item.
     * Add a coloured tooltip to the itemstack, converting the amplifier to roman numerals.
     */
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void toolTipEvent(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        List<String> toolTip = event.getToolTip();

        // Check that the item we're viewing has been crafted using our crafting manager
        // Without this check, we double up the potion tags on potions.
        if ((itemStack.getTagCompound() != null) && (itemStack.getTagCompound().getBoolean("liquid_enchanted"))) {
            List<PotionEffect> potionEffects = LiquidEnchantmentHelper.getPotionTypeFromNBT(itemStack.getTagCompound());

            for (PotionEffect potionEffect : potionEffects) {
                String level = "";
                TextFormatting textFormat = potionEffect.getPotion().isBadEffect() ? TextFormatting.RED : TextFormatting.BLUE;
                String potionName = potionEffect.getPotion().getName();
                if (potionEffect.getAmplifier() > 0) {
                    level = RomanNumber.toRoman(potionEffect.getAmplifier() + 1); // +1 as amplifier is zero-indexed. A Level II potion has an amplifier of 1.
                }
                // Using the I18n library allows the tooltip to be translated if a translation has been provided.
                toolTip.add(1, textFormat + I18n.format(potionName) + " " + level);
            }
        }
    }

}
