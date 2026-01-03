# STS Arena - User Stories

This document describes all user workflows for the STS Arena mod. Each story represents a complete user journey through the mod's functionality.

---

## 1. Main Menu Entry Points

### Story 1.1: Start Arena from Main Menu (Random Loadout)
**As a** player on the main menu
**I want to** start an arena fight with a random loadout
**So that** I can quickly practice combat with varied configurations

**Flow:**
1. Player clicks "Arena Mode" button on main menu
2. Loadout selection screen opens
3. Player selects "New Random Loadout" option
4. Encounter selection screen opens with acts/encounters
5. Player selects an encounter (e.g., "Lagavulin", "The Heart")
6. Arena fight starts with randomly generated deck/relics/potions
7. After fight ends (win/lose), player sees death/victory screen with "Try Again" option
8. Player can retry same fight or return to main menu

**Acceptance Criteria:**
- [ ] Random loadout has valid cards for the character class
- [ ] Relics have appropriate synergies considered
- [ ] Potions are filled based on potion slots
- [ ] HP is set appropriately for the encounter's floor level
- [ ] Ascension level affects monster difficulty

---

### Story 1.2: Start Arena from Main Menu (Saved Loadout)
**As a** player with previously saved loadouts
**I want to** fight using a saved deck configuration
**So that** I can practice with specific builds I've created

**Flow:**
1. Player clicks "Arena Mode" button on main menu
2. Loadout selection screen opens showing saved loadouts
3. Player selects a saved loadout from the list
4. Right panel shows loadout preview (deck, relics, potions, HP)
5. Player clicks "Fight" button
6. Encounter selection screen opens
7. Player selects an encounter
8. Arena fight starts with the saved loadout
9. After fight, loadout is available for retry

**Acceptance Criteria:**
- [ ] Saved loadouts appear in list with name and character class
- [ ] Preview panel shows all deck cards, relics, and potions
- [ ] Clicking a loadout shows its details without starting fight
- [ ] "Fight" button only appears when a saved loadout is selected

---

### Story 1.3: Create Custom Loadout
**As a** player wanting specific configurations
**I want to** build a custom loadout from scratch
**So that** I can test specific card/relic combinations

**Flow:**
1. Player clicks "Arena Mode" on main menu
2. Loadout selection screen opens
3. Player clicks "Create Custom Loadout"
4. Loadout creator screen opens with tabs (Cards, Relics, Potions)
5. Player selects a character class
6. Player adds cards from available pool to deck (left panel → right panel)
7. Player adds relics (clicking toggles add/remove)
8. Player adds potions (up to potion slot limit)
9. Player sets HP values using +/- buttons or direct input
10. Player enters a custom name for the loadout
11. Player clicks "Save & Fight"
12. Loadout is saved, encounter selection opens
13. After fight, custom loadout is available in saved loadouts list

**Acceptance Criteria:**
- [ ] All cards from selected character are available
- [ ] Prismatic Shard relic enables cards from all classes
- [ ] Egg relics auto-upgrade matching card types when added
- [ ] Potion Belt relic adds 2 extra potion slots
- [ ] HP cannot exceed max HP
- [ ] Saved loadout appears in loadout list for future use

---

### Story 1.4: Copy and Modify Loadout
**As a** player with an existing loadout
**I want to** copy it and make modifications
**So that** I can create variations without losing the original

**Flow:**
1. Player opens loadout selection screen
2. Player selects a saved loadout
3. Player clicks "Copy" button
4. Loadout creator opens with copied loadout's configuration
5. Name is prefilled as "Copy of [original name]"
6. Player modifies deck/relics/potions/HP as desired
7. Player saves the new loadout
8. Both original and copy exist as separate loadouts

**Acceptance Criteria:**
- [ ] Copied loadout has identical deck, relics, potions, HP
- [ ] Changes to copy don't affect original
- [ ] Name clearly indicates it's a copy
- [ ] Player can rename before saving

---

### Story 1.5: Rename Loadout
**As a** player with saved loadouts
**I want to** rename a loadout
**So that** I can organize my builds meaningfully

**Flow:**
1. Player opens loadout selection screen
2. Player selects a saved loadout
3. Player clicks "Rename" button
4. Text input field appears with current name
5. Player types new name
6. Player presses Enter to confirm
7. Loadout list updates with new name

**Acceptance Criteria:**
- [ ] Current name is editable
- [ ] Escape cancels rename without saving
- [ ] Empty name is not allowed
- [ ] Name persists across sessions

---

### Story 1.6: Delete Loadout
**As a** player with unwanted loadouts
**I want to** delete a loadout
**So that** my loadout list stays manageable

**Flow:**
1. Player opens loadout selection screen
2. Player selects a saved loadout
3. Player clicks "Delete" button
4. Confirmation dialog appears ("Are you sure?")
5. Player clicks "Yes" to confirm
6. Loadout is removed from list
7. Next loadout in list is auto-selected (if any)

**Acceptance Criteria:**
- [ ] Delete requires confirmation
- [ ] Clicking "No" cancels deletion
- [ ] Associated fight history is preserved (loadout_id becomes null)
- [ ] Auto-select next item after deletion

---

## 2. Practice During Normal Run

### Story 2.1: Practice in Arena from Pause Menu
**As a** player in a normal run
**I want to** practice the current fight in arena mode
**So that** I can try different approaches without risking my run

**Flow:**
1. Player is in a normal run (any floor)
2. Player opens pause menu (ESC)
3. "Practice in Arena" button appears above "Abandon Run"
4. Player clicks "Practice in Arena"
5. Current loadout (deck, relics, potions, HP) is saved
6. Encounter selection screen opens, showing current encounter as "Current Fight"
7. Player selects encounter (current or different)
8. Arena fight starts with current build
9. After fight, player clicks "Leave Arena" in pause menu
10. Player returns to normal run exactly where they left off

**Acceptance Criteria:**
- [ ] Button only appears during normal runs (not arena runs)
- [ ] Loadout captures pre-combat state when in combat
- [ ] Shop purchases made before opening pause menu are preserved
- [ ] Relic counters (e.g., Neow's Lament charges) are preserved
- [ ] Returning to normal run restores exact game state
- [ ] No progress in normal run is affected by arena practice

---

### Story 2.2: Practice in Arena from Shop
**As a** player in a shop
**I want to** test my purchases in arena mode
**So that** I can decide if the purchase is worth it

**Flow:**
1. Player is in a shop during normal run
2. Player purchases a card/relic/potion
3. Player opens pause menu
4. Player clicks "Practice in Arena"
5. Loadout includes the purchased items
6. Player practices in arena
7. Player returns to shop with purchases still intact

**Acceptance Criteria:**
- [ ] Game state is saved before entering arena (preserving purchases)
- [ ] Purchases appear in arena loadout
- [ ] Returning to run keeps all purchases
- [ ] Gold spent is preserved correctly

---

## 3. Post-Fight Workflows

### Story 3.1: Try Again After Arena Victory
**As a** player who won an arena fight
**I want to** retry with the same loadout
**So that** I can optimize my strategy

**Flow:**
1. Player wins arena fight
2. Victory screen shows with "Try Again" and "Done" buttons
3. Player clicks "Try Again"
4. Same loadout loads with reset HP/potions/relics
5. Same encounter starts
6. Process repeats until player clicks "Done"

**Acceptance Criteria:**
- [ ] Loadout resets to pre-fight state (full potions, reset relic counters)
- [ ] Same encounter loads
- [ ] Fight history records each attempt
- [ ] "Done" returns to main menu

---

### Story 3.2: Try Again After Arena Defeat
**As a** player who lost an arena fight
**I want to** retry immediately
**So that** I can practice until I win

**Flow:**
1. Player loses arena fight
2. Death screen shows with "Retreat" and "Try Again" buttons
3. Player clicks "Try Again"
4. Same loadout loads with reset state
5. Same encounter starts
6. Process repeats until player wins or retreats

**Acceptance Criteria:**
- [ ] "Retreat" returns to main menu (shows loadout selection if returnToArena flag set)
- [ ] "Try Again" resets and restarts same fight
- [ ] Each defeat is recorded in fight history
- [ ] HP resets to loadout's starting HP

---

### Story 3.3: Try Again in Arena After Normal Run Defeat
**As a** player who died in a normal run
**I want to** practice that exact fight in arena mode
**So that** I can learn to beat it next time

**Flow:**
1. Player dies in normal run (not abandoning)
2. Death screen shows "Try Again in Arena Mode" button
3. Player clicks the button
4. Loadout from that run (pre-death state) loads
5. The encounter that killed them is selected
6. Arena fight starts
7. After practicing, player returns to main menu

**Acceptance Criteria:**
- [ ] Button only appears after actual deaths (not abandons)
- [ ] Loadout captures pre-combat state (HP and potions before the fatal fight)
- [ ] Relic counters reflect pre-combat values
- [ ] The fatal encounter is auto-selected
- [ ] Normal run save is not affected (can continue if not overwritten)

---

## 4. History and Statistics

### Story 4.1: View Fight History
**As a** player wanting to track progress
**I want to** see my arena fight history
**So that** I can review my performance

**Flow:**
1. Player opens loadout selection screen
2. Player clicks "History" button (bottom right)
3. History screen opens showing all arena runs
4. List shows: Date, Loadout, Encounter, Result (Win/Loss), Turns, Damage
5. Player can scroll through history
6. Player clicks "Return" to go back

**Acceptance Criteria:**
- [ ] History shows all arena runs chronologically
- [ ] Wins shown in green, losses in red
- [ ] Filter by loadout available (via loadout-specific History button)
- [ ] Columns sortable by clicking headers

---

### Story 4.2: View Loadout+Encounter Statistics
**As a** player wanting to analyze performance
**I want to** see statistics by loadout and encounter
**So that** I can identify which fights need more practice

**Flow:**
1. Player opens loadout selection screen
2. Player clicks "Stats" button (bottom right)
3. Stats screen opens showing loadout+encounter combinations
4. Each row shows: Loadout, Encounter, Total Runs, Wins, Losses, Win Rate
5. Player clicks [+] to expand and see Pareto-best victories
6. Pareto victories show: Turns, Damage Taken, Potions Used
7. Player clicks "Return" to go back

**Acceptance Criteria:**
- [ ] Stats grouped by loadout+encounter pair
- [ ] Win rate calculated correctly
- [ ] Pareto-optimal victories identified (not dominated in all metrics)
- [ ] Expandable rows for details

---

## 5. Encounter Selection

### Story 5.1: Select Encounter by Act
**As a** player choosing a fight
**I want to** browse encounters organized by act
**So that** I can find specific fights easily

**Flow:**
1. Encounter selection screen opens after loadout selection
2. Encounters organized under Act 1, Act 2, Act 3 headers
3. Act headers show Normal, Elite, Boss subsections
4. Player scrolls to find desired encounter
5. Player clicks encounter name
6. Fight starts with selected encounter

**Acceptance Criteria:**
- [ ] All base game encounters available
- [ ] Elite encounters include A17+ burning effect at appropriate ascensions
- [ ] Boss encounters scale properly
- [ ] "Current Fight" indicator when entering from pause menu during combat

---

### Story 5.2: Quick-Select Current Fight
**As a** player practicing from pause menu
**I want to** quickly select my current fight
**So that** I don't have to search for it

**Flow:**
1. Player enters arena from pause menu during combat
2. Encounter selection shows current encounter highlighted as "Current Fight"
3. Player clicks "Current Fight" entry
4. Arena fight starts with that encounter

**Acceptance Criteria:**
- [ ] "Current Fight" only appears when coming from combat
- [ ] Correctly identifies the current monster group
- [ ] Works for normal fights, elites, and bosses

---

## 6. Edge Cases and Error Handling

### Story 6.1: Game Crash Recovery
**As a** player whose game crashed during arena
**I want** my normal run to be protected
**So that** I don't lose progress

**Flow:**
1. Player starts arena from normal run
2. Game crashes mid-arena fight
3. Player restarts game
4. On startup, mod detects orphaned arena state
5. Normal run save is restored from backup
6. Player can continue normal run OR start fresh arena

**Acceptance Criteria:**
- [ ] Arena marker file tracks active arena session
- [ ] Backup save restored on startup if marker exists
- [ ] Arena save file cleaned up
- [ ] No data loss in normal run

---

### Story 6.2: Abandon Run vs Death
**As a** player abandoning my run
**I want** the game to NOT create an arena loadout
**So that** my loadout list isn't cluttered with abandoned runs

**Flow:**
1. Player is in normal run
2. Player opens pause menu
3. Player clicks "Abandon Run"
4. Player confirms abandonment
5. Run ends, NO loadout is saved
6. Player returns to main menu

**Acceptance Criteria:**
- [ ] Abandoning run does not save loadout
- [ ] Death (not abandon) does save loadout
- [ ] "Try Again in Arena" button does not appear after abandon

---

## Summary of Key Workflows

| Workflow | Entry Point | Key Features |
|----------|-------------|--------------|
| Random Arena | Main Menu → Arena Mode → New Random | Quick practice, random builds |
| Saved Loadout | Main Menu → Arena Mode → Saved Loadout | Consistent practice |
| Custom Build | Main Menu → Arena Mode → Create Custom | Theory-crafting |
| Practice Mid-Run | Pause Menu → Practice in Arena | Risk-free practice during run |
| Post-Death Practice | Death Screen → Try Again in Arena | Learn from defeat |
| Retry Loop | Victory/Death → Try Again | Master specific fights |
| Statistics | Loadout Screen → Stats | Track progress |

---

## Implementation Notes

- All loadouts saved to SQLite database (`arena.db`)
- Fight history tracks: outcome, turns, damage dealt/taken, potions used
- Pareto-optimal victories = not dominated in (turns, damage taken, potions used)
- Save file backup ensures normal run safety during Practice in Arena
- Egg relics auto-upgrade cards when added to loadout in creator
