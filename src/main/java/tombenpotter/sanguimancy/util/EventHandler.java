package tombenpotter.sanguimancy.util;

import WayofTime.alchemicalWizardry.api.event.ItemBindEvent;
import WayofTime.alchemicalWizardry.api.event.ItemDrainNetworkEvent;
import WayofTime.alchemicalWizardry.api.event.PlayerAddToNetworkEvent;
import WayofTime.alchemicalWizardry.api.event.RitualActivatedEvent;
import WayofTime.alchemicalWizardry.api.soulNetwork.SoulNetworkHandler;
import WayofTime.alchemicalWizardry.common.items.EnergyItems;
import WayofTime.alchemicalWizardry.common.spell.complex.effect.SpellHelper;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.WorldServer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.world.BlockEvent;
import org.lwjgl.opengl.GL11;
import tombenpotter.sanguimancy.Sanguimancy;
import tombenpotter.sanguimancy.api.soulCorruption.SoulCorruptionHelper;
import tombenpotter.sanguimancy.network.PacketHandler;
import tombenpotter.sanguimancy.network.events.EventCorruptedInfusion;
import tombenpotter.sanguimancy.network.packets.PacketSyncCorruption;
import tombenpotter.sanguimancy.registry.BlocksRegistry;
import tombenpotter.sanguimancy.registry.ItemsRegistry;
import tombenpotter.sanguimancy.tile.TileCamouflageBound;
import tombenpotter.sanguimancy.tile.TileItemSNPart;
import tombenpotter.sanguimancy.tile.TileRitualSNPart;
import tombenpotter.sanguimancy.util.singletons.BoundItems;
import tombenpotter.sanguimancy.util.singletons.ClaimedChunks;

import java.util.ArrayList;

public class EventHandler {

    public EventHandler() {
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.modID.equals(Sanguimancy.modid)) {
            ConfigHandler.syncConfig();
            Sanguimancy.logger.info(StatCollector.translateToLocal("info." + Sanguimancy.modid + ".console.config.refresh"));
        }
    }

    @SubscribeEvent
    public void onPlayerSacrificed(LivingDeathEvent event) {
        if (event.entity != null && !event.entity.worldObj.isRemote) {
            if (event.entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) event.entity;
                String owner = player.getCommandSenderName();
                int currentEssence = SoulNetworkHandler.getCurrentEssence(owner);
                if (event.source.getEntity() != null && event.source.getEntity() instanceof EntityPlayer) {
                    EntityPlayer perpetrator = (EntityPlayer) event.source.getEntity();
                    ItemStack attunedStack = new ItemStack(ItemsRegistry.playerSacrificer, 1, 1);
                    if (perpetrator.inventory.hasItemStack(attunedStack)) {
                        perpetrator.inventory.consumeInventoryItem(attunedStack.getItem());
                        ItemStack focusedStack = new ItemStack(ItemsRegistry.playerSacrificer, 1, 2);
                        EnergyItems.checkAndSetItemOwner(focusedStack, owner);
                        focusedStack.stackTagCompound.setInteger("bloodStolen", currentEssence);
                        focusedStack.stackTagCompound.setString("thiefName", perpetrator.getCommandSenderName());
                        perpetrator.inventory.addItemStackToInventory(focusedStack);
                        SoulNetworkHandler.setCurrentEssence(owner, 0);
                        SoulCorruptionHelper.incrementCorruption(player.getDisplayName());
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerJoinWorld(EntityJoinWorldEvent event) {
        if (!event.entity.worldObj.isRemote && event.entity != null && event.entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.entity;
            NBTTagCompound tag = RandomUtils.getModTag(player, Sanguimancy.modid);
            if (!tag.getBoolean("hasInitialChunkClaimer")) {
                player.addChatComponentMessage(new ChatComponentText(StatCollector.translateToLocal("chat.Sanguimancy.intial.claimer")));
                if (!player.inventory.addItemStackToInventory(SanguimancyItemStacks.chunkClaimer.copy())) {
                    RandomUtils.dropItemStackInWorld(player.worldObj, player.posX, player.posY, player.posZ, SanguimancyItemStacks.chunkClaimer.copy());
                }
                tag.setBoolean("hasInitialChunkClaimer", true);
            }

            if (!tag.getBoolean("hasInitialGuide")) {
                if (!player.inventory.addItemStackToInventory(SanguimancyItemStacks.playerGuide.copy())) {
                    RandomUtils.dropItemStackInWorld(player.worldObj, player.posX, player.posY, player.posZ, SanguimancyItemStacks.playerGuide.copy());
                }
                tag.setBoolean("hasInitialGuide", true);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        String playerName;
        if (!event.player.worldObj.isRemote) playerName = event.player.getDisplayName();
        else playerName = Sanguimancy.proxy.getClientPlayer().getDisplayName();

        if (SoulCorruptionHelper.isCorruptionOver(playerName, 10)) {
            SoulCorruptionHelper.spawnChickenFollower(event.player);
        }
        if (SoulCorruptionHelper.isCorruptionOver(playerName, 40)) {
            SoulCorruptionHelper.killGrass(event.player);
        }
        if (SoulCorruptionHelper.isCorruptionOver(playerName, 60)) {
            SoulCorruptionHelper.hurtAndHealAnimals(event.player);
        }
        if (SoulCorruptionHelper.isCorruptionOver(playerName, 100)) {
            SoulCorruptionHelper.spawnIllusion(event.player);
        }
        if (SoulCorruptionHelper.isCorruptionOver(playerName, 150)) {
            SoulCorruptionHelper.randomTeleport(event.player);
        }
        if (SoulCorruptionHelper.isCorruptionOver(playerName, 200)) {
            SoulCorruptionHelper.loseHeart(event.player);
        }
        if (!event.player.worldObj.isRemote && event.player.worldObj.getWorldTime() % 200 == 0) {
            PacketHandler.INSTANCE.sendToAll(new PacketSyncCorruption(event.player.getDisplayName()));
        }
    }

    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent event) {
        if (event.entityPlayer != null && event.target != null && event.target instanceof EntityLivingBase) {
            EntityLivingBase target = (EntityLivingBase) event.target;
            if (SoulCorruptionHelper.isCorruptionOver(event.entityPlayer.getDisplayName(), 30))
                SoulCorruptionHelper.addWither(target);
        }
    }

    @SubscribeEvent
    public void onRitualActivation(RitualActivatedEvent event) {
        if (event.player != null) {
            if (SoulCorruptionHelper.isCorruptionOver(event.player.getDisplayName(), 50) && event.player.worldObj.rand.nextInt(10) == 0) {
                event.setResult(Event.Result.DENY);
            }
        }
    }

    @SubscribeEvent
    public void onChunkForce(ForgeChunkManager.ForceChunkEvent event) {
        if (!Loader.isModLoaded("loaderlist")) {
            RandomUtils.writeLog(event.ticket.getModId() + " forcing the loading of the chunk at x= " + String.valueOf(event.location.getCenterXPos()) + " and z=" + String.valueOf(event.location.getCenterZPosition()) + " in dimension " + String.valueOf(event.ticket.world.provider.dimensionId), "ChunkloadingLog.txt");
        }
    }

    @SubscribeEvent
    public void onChunkUnforce(ForgeChunkManager.UnforceChunkEvent event) {
        if (!Loader.isModLoaded("loaderlist")) {
            RandomUtils.writeLog(event.ticket.getModId() + " unforcing the loading of the chunk at x= " + String.valueOf(event.location.getCenterXPos()) + " and z=" + String.valueOf(event.location.getCenterZPosition()) + " in dimension " + String.valueOf(event.ticket.world.provider.dimensionId), "ChunkloadingLog.txt");
        }
    }

    @SubscribeEvent
    public void onItemAddedToSN(ItemBindEvent event) {
        if (!event.player.worldObj.isRemote) {
            if (event.player.inventory.hasItemStack(SanguimancyItemStacks.etherealManifestation)) {
                int dimID = ConfigHandler.snDimID;
                WorldServer dimWorld = MinecraftServer.getServer().worldServerForDimension(dimID);
                if (ClaimedChunks.getClaimedChunks().getLinkedChunks(event.player.getCommandSenderName()) != null) {
                    for (ChunkIntPairSerializable chunkInt : ClaimedChunks.getClaimedChunks().getLinkedChunks(event.player.getCommandSenderName())) {
                        int baseX = (chunkInt.chunkXPos << 4) + (dimWorld.rand.nextInt(16));
                        int baseZ = (chunkInt.chunkZPos << 4) + (dimWorld.rand.nextInt(16));
                        int baseY = dimWorld.getTopSolidOrLiquidBlock(baseX, baseZ) + 2;
                        if (baseY >= 128) {
                            continue;
                        }
                        BoundItemState boundItemState = new BoundItemState(baseX, baseY, baseZ, dimID, true);
                        String name = String.valueOf(dimID) + String.valueOf(baseX) + String.valueOf(baseY) + String.valueOf(baseZ) + event.itemStack.getUnlocalizedName() + event.itemStack.getDisplayName() + event.itemStack.getItemDamage() + event.player.getCommandSenderName();
                        if (dimWorld.isAirBlock(baseX, baseY, baseZ)) {
                            RandomUtils.checkAndSetCompound(event.itemStack);
                            if (BoundItems.getBoundItems().addItem(name, boundItemState)) {
                                dimWorld.setBlock(baseX, baseY, baseZ, BlocksRegistry.boundItem);
                                event.itemStack.stackTagCompound.setString("SavedItemName", name);
                                if (dimWorld.getTileEntity(baseX, baseY, baseZ) != null && dimWorld.getTileEntity(baseX, baseY, baseZ) instanceof TileItemSNPart) {
                                    TileItemSNPart tile = (TileItemSNPart) dimWorld.getTileEntity(baseX, baseY, baseZ);
                                    tile.setInventorySlotContents(0, event.itemStack.copy());
                                    tile.getCustomNBTTag().setString("SavedItemName", name);
                                    dimWorld.markBlockForUpdate(baseX, baseY, baseZ);
                                }
                            }
                        }
                        event.player.addChatComponentMessage(new ChatComponentText(StatCollector.translateToLocal("chat.Sanguimancy.added.success")));
                        dimWorld.markBlockForUpdate(baseX, baseY, baseZ);
                        break;
                    }
                }
                event.player.inventory.consumeInventoryItem(SanguimancyItemStacks.etherealManifestation.getItem());
            }
        }
    }

    @SubscribeEvent
    public void onItemDrainNetwork(ItemDrainNetworkEvent event) {
        if (!event.player.worldObj.isRemote && event.itemStack != null) {
            if (event.itemStack.stackTagCompound.hasKey("SavedItemName")) {
                String name = event.itemStack.stackTagCompound.getString("SavedItemName");
                if (!BoundItems.getBoundItems().hasKey(name)) {
                    event.player.addChatComponentMessage(new ChatComponentText(StatCollector.translateToLocal("chat.Sanguimancy.removed")));
                    RandomUtils.unbindItemStack(event.itemStack);
                    event.setResult(Event.Result.DENY);
                } else if (!BoundItems.getBoundItems().getLinkedLocation(name).activated) {
                    event.player.addChatComponentMessage(new ChatComponentText(StatCollector.translateToLocal("chat.Sanguimancy.deactivated")));
                    event.setResult(Event.Result.DENY);
                }
            }
        }
    }

    @SubscribeEvent
    public void onItemAddToNetwork(PlayerAddToNetworkEvent event) {
        if (!event.player.worldObj.isRemote && event.itemStack != null) {
            if (event.itemStack.stackTagCompound.hasKey("SavedItemName")) {
                String name = event.itemStack.stackTagCompound.getString("SavedItemName");
                if (!BoundItems.getBoundItems().hasKey(name)) {
                    event.player.addChatComponentMessage(new ChatComponentText(StatCollector.translateToLocal("chat.Sanguimancy.removed")));
                    event.setResult(Event.Result.DENY);
                } else if (!BoundItems.getBoundItems().getLinkedLocation(name).activated) {
                    event.player.addChatComponentMessage(new ChatComponentText(StatCollector.translateToLocal("chat.Sanguimancy.deactivated")));
                    event.setResult(Event.Result.DENY);
                }
            }
        }
    }

    @SubscribeEvent
    public void onRitualStart(RitualActivatedEvent event) {
        if (!event.player.worldObj.isRemote) {
            if (event.player.inventory.hasItemStack(SanguimancyItemStacks.etherealManifestation)) {
                int dimID = ConfigHandler.snDimID;
                WorldServer dimWorld = MinecraftServer.getServer().worldServerForDimension(dimID);
                if (ClaimedChunks.getClaimedChunks().getLinkedChunks(event.player.getCommandSenderName()) != null) {
                    for (ChunkIntPairSerializable chunkInt : ClaimedChunks.getClaimedChunks().getLinkedChunks(event.player.getCommandSenderName())) {
                        int baseX = (chunkInt.chunkXPos << 4) + (dimWorld.rand.nextInt(16));
                        int baseZ = (chunkInt.chunkZPos << 4) + (dimWorld.rand.nextInt(16));
                        int baseY = dimWorld.getTopSolidOrLiquidBlock(baseX, baseZ) + 2;
                        if (baseY >= 128) {
                            continue;
                        }
                        if (dimWorld.isAirBlock(baseX, baseY, baseZ)) {
                            dimWorld.setBlock(baseX, baseY, baseZ, BlocksRegistry.ritualRepresentation);
                            if (dimWorld.getTileEntity(baseX, baseY, baseZ) != null && dimWorld.getTileEntity(baseX, baseY, baseZ) instanceof TileRitualSNPart) {
                                TileRitualSNPart part = (TileRitualSNPart) dimWorld.getTileEntity(baseX, baseY, baseZ);
                                part.xRitual = event.mrs.getXCoord();
                                part.yRitual = event.mrs.getYCoord();
                                part.zRitual = event.mrs.getZCoord();
                                dimWorld.markBlockForUpdate(baseX, baseY, baseZ);
                            }
                        }
                        event.player.addChatComponentMessage(new ChatComponentText(StatCollector.translateToLocal("chat.Sanguimancy.added.success")));
                        break;
                    }
                }
                event.player.inventory.consumeInventoryItem(SanguimancyItemStacks.etherealManifestation.getItem());
            }
        }
    }

    @SubscribeEvent
    public void onPlayerCorruptedInfusionAesthetic(EventCorruptedInfusion.EventPlayerCorruptedInfusion event) {
        double posX = event.entityPlayer.posX;
        double posY = event.entityPlayer.posY;
        double posZ = event.entityPlayer.posZ;
        event.entityPlayer.worldObj.playSoundEffect((double) ((float) posX + 0.5F), (double) ((float) posY + 0.5F), (double) ((float) posZ + 0.5F), "random.fizz", 0.5F, 2.6F + (event.entityPlayer.worldObj.rand.nextFloat() - event.entityPlayer.worldObj.rand.nextFloat()) * 0.8F);
        SpellHelper.sendIndexedParticleToAllAround(event.entityPlayer.worldObj, posX, posY, posZ, 20, event.entityPlayer.worldObj.provider.dimensionId, 4, posX, posY, posZ);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    //This code is very much inspired by the one in ProfMobius' Waila mod
    public void onSanguimancyItemTooltip(ItemTooltipEvent event) {
        try {
            ModContainer mod = GameData.findModOwner(GameData.itemRegistry.getNameForObject(event.itemStack.getItem()));
            String modname = mod == null ? "Minecraft" : mod.getName();
            if (modname.equals(Sanguimancy.name) && event.itemStack.stackTagCompound != null && event.itemStack.stackTagCompound.hasKey("ownerName")) {
                if (GuiScreen.isShiftKeyDown()) {
                    event.toolTip.add((StatCollector.translateToLocal("info.Sanguimancy.tooltip.owner") + ": " + RandomUtils.getItemOwner(event.itemStack)));
                }
            }
        } catch (NullPointerException e) {
            Sanguimancy.logger.info("No mod found for this item");
        }
    }

    @SubscribeEvent
    public void onBreakBoundTile(BlockEvent.BreakEvent event) {
        if (event.world.getTileEntity(event.x, event.y, event.z) != null && event.world.getTileEntity(event.x, event.y, event.z) instanceof TileCamouflageBound) {
            TileCamouflageBound tile = (TileCamouflageBound) event.world.getTileEntity(event.x, event.y, event.z);
            if (tile.getOwnersList() == null) tile.setOwnersList(new ArrayList<String>());
            if (!tile.getOwnersList().isEmpty() && !tile.getOwnersList().contains(event.getPlayer().getCommandSenderName())) {
                event.getPlayer().addChatComponentMessage(new ChatComponentText(StatCollector.translateToLocal("info.Sanguimancy.tooltip.wrong.player")));
                event.setCanceled(true);
            }
        }
    }

    public static class ClientEventHandler {
        /*
        public static KeyBinding keySearchPlayer = new KeyBinding(StatCollector.translateToLocal("key.Sanguimancy.search"), Keyboard.KEY_F, Sanguimancy.modid);

        public ClientEventHandler() {
            ClientRegistry.registerKeyBinding(keySearchPlayer);
        }

        @SubscribeEvent
        public void onKeyInput(InputEvent.KeyInputEvent event) {
            if (keySearchPlayer.isPressed()) {
                PacketHandler.INSTANCE.sendToServer(new PacketPlayerSearch());
            }
        }
        */

        public ClientEventHandler() {
        }

        @SubscribeEvent
        public void onRenderPlayerSpecialAntlers(RenderPlayerEvent.Specials.Post event) {
            String names[] = {"Tombenpotter", "Speedynutty68", "WayofFlowingTime", "Jadedcat", "Kris1432", "Drullkus", "TheOrangeGenius", "Direwolf20", "Pahimar", "ValiarMarcus", "Alex_hawks", "StoneWaves", "DemoXin", "insaneau"};
            for (String name : names) {
                if (event.entityPlayer.getCommandSenderName().equalsIgnoreCase(name)) {
                    GL11.glPushMatrix();
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                    event.renderer.modelBipedMain.bipedBody.render(0.1F);
                    Minecraft.getMinecraft().renderEngine.bindTexture(new ResourceLocation(Sanguimancy.texturePath + ":textures/items/Wand.png"));
                    GL11.glTranslatef(0.0F, -0.95F, -0.125F);
                    Tessellator tesselator = Tessellator.instance;

                    GL11.glPushMatrix();
                    GL11.glRotatef(-20.0F, 0.0F, 1.0F, 0.0F);
                    GL11.glRotatef(-5.0F, 0.0F, 1.0F, 0.0F);
                    tesselator.startDrawingQuads();
                    tesselator.addVertexWithUV(0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
                    tesselator.addVertexWithUV(0.0D, 1.0D, 0.0D, 0.0D, 1.0D);
                    tesselator.addVertexWithUV(1.0D, 1.0D, 0.0D, 1.0D, 1.0D);
                    tesselator.addVertexWithUV(1.0D, 0.0D, 0.0D, 1.0D, 0.0D);
                    tesselator.draw();
                    GL11.glPopMatrix();

                    GL11.glPushMatrix();
                    GL11.glRotatef(5.0F, 0.0F, 1.0F, 0.0F);
                    GL11.glRotatef(20.0F, 0.0F, 1.0F, 0.0F);
                    tesselator.startDrawingQuads();
                    tesselator.addVertexWithUV(0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
                    tesselator.addVertexWithUV(0.0D, 1.0D, 0.0D, 0.0D, 1.0D);
                    tesselator.addVertexWithUV(-1.0D, 1.0D, 0.0D, 1.0D, 1.0D);
                    tesselator.addVertexWithUV(-1.0D, 0.0D, 0.0D, 1.0D, 0.0D);
                    tesselator.draw();
                    GL11.glPopMatrix();
                    GL11.glPopMatrix();
                }
            }
        }

        @SubscribeEvent
        public void prePlayerRender(RenderPlayerEvent.Pre event) {
            if (SoulCorruptionHelper.getClientPlayerCorruption() >= 20) {
                GL11.glPushMatrix();
                GL11.glDisable(2929);
                GL11.glColor3f(255, 0, 0);
                GL11.glPopMatrix();
            }
        }

        @SubscribeEvent
        public void postPlayerRender(RenderPlayerEvent.Post event) {
            if (SoulCorruptionHelper.getClientPlayerCorruption() >= 20) {
                GL11.glPushMatrix();
                GL11.glEnable(2929);
                GL11.glPopMatrix();
            }
        }
    }
}
