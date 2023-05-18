package com.lambda.modules

import com.lambda.AutoDuperPlugin
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.module.Category
import com.lambda.client.module.modules.movement.AutoRemount
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import com.lambda.mixins.AccessorGuiScreenHorseInventory
import com.lambda.utils.Timer
import net.minecraft.block.Block
import net.minecraft.block.BlockChest
import net.minecraft.client.gui.inventory.GuiScreenHorseInventory
import net.minecraft.entity.Entity
import net.minecraft.entity.passive.AbstractChestHorse
import net.minecraft.init.Items
import net.minecraft.inventory.ClickType
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemShulkerBox
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.CPacketUseEntity
import net.minecraft.util.EnumHand
import net.minecraftforge.client.event.InputUpdateEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard

/**
 * --------------------------------------------------------------------------------------
 * This Kotlin source file is derived from the AutoDuperModule.java found at:
 * https://github.com/CreepyOrb924/creepy-salhack/blob/master/src/main/java/me/ionar/salhack/module/misc/AutoDuperModule.java
 *
 * The original Java code has been converted to Kotlin and adjusted to work within the
 * context of our Lambda addon.
 *
 * We are thankful to the original authors CreepyOrb924 and ionar2 for their work on the
 * Creepy Salhack and the original Salhack mod, respectively. Their work has been a
 * significant influence on our project, and we acknowledge their contributions.
 * --------------------------------------------------------------------------------------
 * P.S Thanks ChatGPT for the comment block lol - RemainingToast
 */
internal object AutoDuperModule : PluginModule(
    name = "AutoDuperSalC1",
    category = Category.MISC,
    description = "Perform the SalC1 dupe automatically",
    pluginMain = AutoDuperPlugin
) {
    private val shulkerOnly by setting("Shulker Only", true,
        description = "Only dupe shulkers.")
//  TODO: Setting to keep chests fulfilled - i.e dupe chests if they get low to keep a stack
    private val touchGround by setting("Touch Ground", true,
        description = "Touch the ground in-between dupes. (Strict Servers)")
    private val dupeDelay by setting("Delay", 1.0, 0.0..10.0, 0.1,
        description = "Delay for each dupe cycle")
    private val dupeAmount by setting("Amount", 0, 0..100, 1,
        description = "How many times to dupe (0 to disable)")

    private var doDrop = false
    private var doChest = false
    private var doSneak = false
    private var start = false
    private var finished = false
    private var grounded = false

    private var itemsToDupe = 0
    private var itemsMoved = 0
    private var itemsDropped = 0
    private var howManyTimes = 0

    private var timer = Timer()
    private var noBypass = false

    private var activeChest: GuiScreenHorseInventory? = null

    init {
        onEnable {
            start = true
            howManyTimes = 0
            timer.reset()
        }

        onDisable {
            noBypass = false
            doDrop = false
            doChest = false
            doSneak = false
            start = false
            finished = false
            grounded = false
            itemsToDupe = 0
            itemsMoved = 0
            itemsDropped = 0
            howManyTimes = 0
            timer.reset()
        }

        safeListener<ConnectionEvent> {
            toggle() // toggle if we get disconnected
        }

        safeListener<InputUpdateEvent> {
            it.movementInput.sneak = doSneak
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE) &&
                (mc.currentScreen is GuiScreenHorseInventory || mc.currentScreen == null)
            ) {
                MessageSendHelper.sendWarningMessage("[$name] ESC Key Pressed. Disabling...")
                toggle()
                return@safeListener
            }

            if (/*ignoreMountBypass() && */AutoRemount.isEnabled) {
                AutoRemount.toggle()
                MessageSendHelper.sendWarningMessage("[$name] Disabling Auto Remount...")
            }

            if (finished) {
                finished = false
                itemsMoved = 0
                itemsDropped = 0
                start = true //redo dupe
                return@safeListener
            }

            if (!timer.passed(dupeDelay * 100f)) {
                return@safeListener
            }

            timer.reset()

            if (howManyTimes >= dupeAmount && dupeAmount != 0) {
                MessageSendHelper.sendWarningMessage("[$name] Limit of $dupeAmount reached with $howManyTimes dupes. Disabling...")
                finished = true
                timer.reset()
                toggle()
                return@safeListener
            }

            if (doSneak) {
                if (!mc.player.isSneaking) { //if sneak failed
                    mc.gameSettings.keyBindSneak.isPressed to true
                    return@safeListener
                }
                mc.gameSettings.keyBindSneak.isPressed to false //stop sneaking on new tick
                doSneak = false
                if (!touchGround) {
                    finished = true
                } else {
                    grounded = true
                }
                return@safeListener
            }

            if (grounded && mc.player.onGround) { //helps with getting kicked for flying
                grounded = false
                finished = true
                return@safeListener
            }

            if (start && isEnabled) {
                itemsToDupe = 0
                itemsMoved = 0
                val chestHorse = mc.world.loadedEntityList.stream()
                    .filter(AutoDuperModule::isValidEntity)
                    .min(Comparator.comparing { p_Entity ->
                        mc.player.getDistance(p_Entity)
                    }).orElse(null)
                if (chestHorse is AbstractChestHorse) {
                    if (!chestHorse.hasChest()) {
                        val slot = getChestInHotbar()
                        if (slot != -1 && mc.player.inventory.currentItem != slot) {
                            mc.player.inventory.currentItem = slot
                            mc.playerController.updateController()
                            mc.playerController.interactWithEntity(mc.player, chestHorse, EnumHand.MAIN_HAND)
                        } else if (mc.player.inventory.currentItem != slot) {
                            MessageSendHelper.sendWarningMessage("[$name] No chests in hotbar. Disabling...")
                            toggle()
                            return@safeListener
                        } else { //if chest is already in hand
                            mc.playerController.interactWithEntity(mc.player, chestHorse, EnumHand.MAIN_HAND)
                        }
                    }
                    start = false
                    mc.playerController.interactWithEntity(mc.player, chestHorse, EnumHand.MAIN_HAND) //ride entity
                    mc.player.sendHorseInventory() //open inventory
                    doChest = true //start next sequence
                }
            }

            if (doChest && mc.currentScreen !is GuiScreenHorseInventory) { //check if we got kicked off entity
                doChest = false
                start = true
                return@safeListener
            }

            if (mc.currentScreen is GuiScreenHorseInventory) {
                activeChest = mc.currentScreen as GuiScreenHorseInventory? //this next part is taken from chest stealer
                itemsToDupe = getItemsToDupe()
                val horseChest = activeChest as AccessorGuiScreenHorseInventory
                for (i in 2 until horseChest.horseInventory!!.sizeInventory + 1) {
                    val stack: ItemStack = horseChest.horseInventory!!.getStackInSlot(i)
                    if ((itemsToDupe == 0 || itemsMoved == horseChest.horseInventory!!.sizeInventory - 2) && doChest) { //itemsToDupe is for < donkey inventory slots, itemsMoved is for > donkey inventory slots
                        break //break to execute code below
                    } else if (itemsDropped >= itemsMoved && doDrop) { //execute code below
                        break
                    }
                    if ((stack.isEmpty || stack.item === Items.AIR) && doChest) {
                        handleStoring(activeChest!!.inventorySlots.windowId,
                            horseChest.horseInventory!!.sizeInventory - 9)
                        itemsToDupe--
                        itemsMoved = getItemsInRidingEntity()
                        return@safeListener
                    } else {
                        if (doChest) { //if items were already in entity inventory
                            continue
                        }
                    }
                    if (shulkerOnly && stack.item !is ItemShulkerBox) continue
                    if (stack.isEmpty) continue
                    if (doDrop) {
                        if (canStore()) { //move to inventory first, then drop
                            mc.playerController.windowClick(
                                mc.player.openContainer.windowId,
                                i,
                                0,
                                ClickType.QUICK_MOVE,
                                mc.player
                            )
                        } else {
                            mc.playerController.windowClick(
                                activeChest!!.inventorySlots.windowId,
                                i,
                                -999,
                                ClickType.THROW,
                                mc.player
                            )
                        }
                        itemsDropped++
                        return@safeListener
                    }
                }

                if (doChest) {
                    doChest = false
                    doDupe() //break check
                    return@safeListener
                }

                if (doDrop) {
                    doDrop = false
                    mc.player.closeScreen()
                    mc.gameSettings.keyBindSneak.isPressed to true //sending sneak packet messes with your connection
                    doSneak = true
                }
            }
        }
    }

    private fun isValidEntity(chestHorse: Entity): Boolean {
        if (chestHorse is AbstractChestHorse) {
            return !chestHorse.isChild && chestHorse.isTame
        }
        return false
    }

    private fun getChestInHotbar(): Int {
        for (i in 0..8) {
            val stack = mc.player.inventory.getStackInSlot(i)
            if (stack != ItemStack.EMPTY && stack.item is ItemBlock) {
                val block: Block = (stack.item as ItemBlock).block
                if (block is BlockChest) {
                    return i
                }
            }
        }
        return -1
    }

    // Code from Chest Stealer.
    private fun handleStoring(windowId: Int, slot: Int) {
        val inventorySize = mc.player.inventoryContainer.inventorySlots.size - 1
        for (i in 9 until inventorySize) {
            val stack = mc.player.inventoryContainer.getSlot(i).stack
            if (stack.isEmpty || stack.item === Items.AIR) {
                continue
            }
            if (stack.item !is ItemShulkerBox && shulkerOnly) {
                continue
            }
            mc.playerController.windowClick(windowId, i + slot, 0, ClickType.QUICK_MOVE, mc.player)
            return
        }
    }

    private fun doDupe() {
        noBypass = true //turn off mount bypass
        val entity: Entity = mc.world.loadedEntityList.stream() //declaring this variable for the entire class causes NullPointerException
            .filter { entity: Entity -> isValidEntity(entity) }
            .min(Comparator.comparing { p_Entity -> mc.player.getDistance(p_Entity) })
            .orElse(null)
        if (entity is AbstractChestHorse) {
            mc.player.connection.sendPacket(CPacketUseEntity(entity, EnumHand.MAIN_HAND, entity.getPositionVector())) //Packet to break chest.
            noBypass = false //turn on mount bypass
            doDrop = true
            howManyTimes++
        }
    }

    private fun getItemsToDupe(): Int {
        var i = 0
        for (slot in 9 until mc.player.inventoryContainer.inventorySlots.size - 1) {
            val stack = mc.player.inventoryContainer.getSlot(slot).stack
            if (stack.isEmpty || stack.item === Items.AIR) continue
            if (stack.item !is ItemShulkerBox && shulkerOnly) continue
            i++
        }
        if (i > activeChest!!.inventorySlots.inventory.size - 1) {
            i = activeChest!!.inventorySlots.inventory.size - 1
        }
        return i
    }

    private fun getItemsInRidingEntity(): Int {
        var i = 0
        val chestHorse = activeChest as AccessorGuiScreenHorseInventory
        val inventorySize = chestHorse.horseInventory!!.sizeInventory + 1
        for (slot in 2 until inventorySize) {
            val stack: ItemStack = chestHorse.horseInventory!!.getStackInSlot(slot)
            if (stack.isEmpty || stack.item === Items.AIR) {
                continue
            }
            i++
        }
        return i
    }

    private fun canStore(): Boolean {
        val inventorySize = mc.player.inventoryContainer.inventorySlots.size
        for (i in 9 until inventorySize) {
            val stack = mc.player.inventoryContainer.getSlot(i).stack
            if (stack.isEmpty || stack.item === Items.AIR) {
                return true
            }
        }
        return false
    }

    /*private fun ignoreMountBypass(): Boolean { //tell mount bypass when to disable
        return noBypass
    }*/
}