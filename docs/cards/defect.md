# Defect (Blue) Cards Reference

This document provides reference information for all Defect cards.

## Card ID Naming

Defect cards generally use **spaces in their IDs** (e.g., "Ball Lightning", "Cold Snap"). Some notable exceptions:

| Display Name | Card ID | Notes |
|--------------|---------|-------|
| Boot Sequence | `BootSequence` | No space, Innate+Exhaust |
| Go for the Eyes | `Go for the Eyes` | Has spaces |
| FTL | `FTL` | All caps |
| Multi-Cast | `Multi-Cast` | Has hyphen |

---

## Basic Cards

| Card ID | Display Name | Type | Cost | Effect |
|---------|--------------|------|------|--------|
| `Strike_B` | Strike | ATTACK | 1 | Deal 6 damage (STRIKE, STARTER_STRIKE tags) |
| `Defend_B` | Defend | SKILL | 1 | Gain 5 block (STARTER_DEFEND tag) |
| `Zap` | Zap | SKILL | 1 | Channel 1 Lightning |
| `Dualcast` | Dualcast | SKILL | 1 | Evoke your rightmost orb twice |

---

## Orb Types

| Orb | Passive (per turn) | Evoke Effect |
|-----|-------------------|--------------|
| **Lightning** | Deal 3 damage to random enemy | Deal 8 damage to random enemy |
| **Frost** | Gain 2 block | Gain 5 block |
| **Dark** | +6 damage stored | Deal stored damage to lowest HP enemy |
| **Plasma** | Gain 1 energy | Gain 2 energy |

---

## Common Cards

### Attacks
| Card ID | Display Name | Cost | Effect |
|---------|--------------|------|--------|
| `Ball Lightning` | Ball Lightning | 1 | Deal 7 damage, Channel 1 Lightning |
| `Barrage` | Barrage | 1 | Deal 4 damage per orb channeled |
| `Beam Cell` | Beam Cell | 0 | Deal 3 damage, apply 1 Vulnerable |
| `Cold Snap` | Cold Snap | 1 | Deal 6 damage, Channel 1 Frost |
| `Compile Driver` | Compile Driver | 1 | Deal 7 damage, draw 1 per unique orb type |
| `Go for the Eyes` | Go for the Eyes | 0 | Deal 3 damage, if enemy attacking: apply 1 Weak |
| `Rebound` | Rebound | 1 | Deal 9 damage, next card played returns to draw pile |
| `Streamline` | Streamline | 2 | Deal 15 damage, reduce this card's cost by 1 |
| `Sweeping Beam` | Sweeping Beam | 1 | Deal 6 damage to ALL, draw 1 |

### Skills
| Card ID | Display Name | Cost | Effect |
|---------|--------------|------|--------|
| `BootSequence` | Boot Sequence | 0 | Gain 10 block, Innate, Exhaust |
| `Chaos` | Chaos | 1 | Channel 1 random orb |
| `Chill` | Chill | 0 | Channel 1 Frost per enemy, Exhaust |
| `Coolheaded` | Coolheaded | 1 | Channel 1 Frost, draw 1 |
| `Hologram` | Hologram | 1 | Gain 3 block, put card from discard in hand, Exhaust |
| `Leap` | Leap | 1 | Gain 9 block |
| `Recursion` | Recursion | 1 | Evoke rightmost orb, Channel same orb |
| `Stack` | Stack | 1 | Gain block equal to cards in discard pile |
| `Steam Barrier` | Steam Barrier | 0 | Gain 6 block, reduce this card's block by 1 |
| `Turbo` | Turbo | 0 | Gain 2 energy, add Void to discard |

---

## Uncommon Cards

### Attacks
| Card ID | Display Name | Cost | Effect |
|---------|--------------|------|--------|
| `Blizzard` | Blizzard | 1 | Deal 2 damage to ALL per Frost channeled this combat |
| `Doom and Gloom` | Doom and Gloom | 2 | Deal 10 damage to ALL, Channel 1 Dark |
| `FTL` | FTL | 0 | Deal 5 damage, if 3+ cards played: draw 1 |
| `Lockon` | Lock-On | 1 | Deal 8 damage, apply 2 Lock-On (orbs target this enemy) |
| `Melter` | Melter | 1 | Remove enemy block, deal 10 damage |
| `Rip and Tear` | Rip and Tear | 1 | Deal 7 damage twice |
| `Sunder` | Sunder | 3 | Deal 24 damage, if enemy dies: gain 3 energy |

### Skills
| Card ID | Display Name | Cost | Effect |
|---------|--------------|------|--------|
| `Aggregate` | Aggregate | 1 | Gain 1 energy per 4 cards in draw pile |
| `Auto Shields` | Auto-Shields | 1 | If no block: gain 11 block |
| `Consume` | Consume | 2 | Gain 2 Focus, lose 1 orb slot |
| `Darkness` | Darkness | 1 | Channel 1 Dark |
| `Double Energy` | Double Energy | 1 | Double current energy, Exhaust |
| `Equilibrium` | Equilibrium | 2 | Gain 13 block, Retain hand this turn |
| `Force Field` | Force Field | 4 | Gain 12 block, costs 1 less per Power played |
| `Fusion` | Fusion | 2 | Channel 1 Plasma |
| `Genetic Algorithm` | Genetic Algorithm | 1 | Gain 1 block, permanently gain +2 block, Exhaust |
| `Glacier` | Glacier | 2 | Gain 7 block, Channel 2 Frost |
| `Impulse` | Impulse | 0 | Evoke all orbs |
| `Overclock` | Overclock | 0 | Draw 2, add Burn to discard |
| `Recycle` | Recycle | 1 | Exhaust a card, gain energy equal to its cost |
| `Reinforced Body` | Reinforced Body | X | Gain 7 block X times |
| `Skim` | Skim | 1 | Draw 3 |
| `White Noise` | White Noise | 1 | Add random Power to hand costing 0, Exhaust |

### Powers
| Card ID | Display Name | Cost | Effect |
|---------|--------------|------|--------|
| `Capacitor` | Capacitor | 1 | Gain 2 orb slots |
| `Defragment` | Defragment | 1 | Gain 1 Focus |
| `Heatsinks` | Heatsinks | 1 | When playing Power: draw 1 |
| `Hello World` | Hello World | 1 | At turn start: add random Common to hand |
| `Loop` | Loop | 1 | At turn start: trigger rightmost orb passive |
| `Self Repair` | Self Repair | 1 | At end of combat: heal 7 HP (HEALING tag) |
| `Static Discharge` | Static Discharge | 1 | When taking unblocked damage: Channel 1 Lightning |
| `Storm` | Storm | 1 | When playing Power: Channel 1 Lightning |

---

## Rare Cards

### Attacks
| Card ID | Display Name | Cost | Effect |
|---------|--------------|------|--------|
| `All For One` | All For One | 2 | Deal 10 damage, return all 0-cost cards from discard to hand |
| `Hyperbeam` | Hyperbeam | 2 | Deal 26 damage to ALL, lose 3 Focus |
| `Meteor Strike` | Meteor Strike | 5 | Deal 24 damage, Channel 3 Plasma |
| `Thunder Strike` | Thunder Strike | 3 | Deal 7 damage to random enemy per Lightning channeled |

### Skills
| Card ID | Display Name | Cost | Effect |
|---------|--------------|------|--------|
| `Amplify` | Amplify | 1 | Next Power is played twice |
| `Fission` | Fission | 0 | Remove all orbs, gain 1 energy and draw 1 per orb, Exhaust |
| `Multi-Cast` | Multi-Cast | X | Evoke rightmost orb X times |
| `Rainbow` | Rainbow | 2 | Channel 1 Lightning, 1 Frost, 1 Dark, Exhaust |
| `Reboot` | Reboot | 0 | Shuffle all cards into draw pile, draw 4, Exhaust |
| `Reprogram` | Reprogram | 1 | Lose 1 Focus, gain 1 Strength and 1 Dexterity |
| `Seek` | Seek | 0 | Choose 1 card from draw pile and put in hand, Exhaust |
| `Tempest` | Tempest | X | Channel X Lightning, Exhaust |

### Powers
| Card ID | Display Name | Cost | Effect |
|---------|--------------|------|--------|
| `Biased Cognition` | Biased Cognition | 1 | Gain 4 Focus, lose 1 Focus at turn start |
| `Buffer` | Buffer | 2 | Prevent next HP loss |
| `Creative AI` | Creative AI | 3 | At turn start: add random Power to hand |
| `Echo Form` | Echo Form | 3 | First card each turn plays twice, Ethereal |
| `Electrodynamics` | Electrodynamics | 2 | Lightning hits ALL enemies, Channel 2 Lightning |
| `Machine Learning` | Machine Learning | 1 | At turn start: draw 1 more card |

---

## Key Synergies

### Focus Synergies
- `Defragment`, `Consume`, `Biased Cognition` - gain Focus
- `Hyperbeam`, `Reprogram` - lose Focus
- Orb passive/evoke damage scales with Focus

### Lightning Synergies
- `Ball Lightning`, `Cold Snap`, `Zap` - Channel Lightning
- `Thunder Strike` - damage per Lightning channeled
- `Electrodynamics` - Lightning hits ALL
- `Storm` - Channel Lightning on Power play
- `Static Discharge` - Channel on damage taken

### Frost Synergies
- `Coolheaded`, `Glacier`, `Chill` - Channel Frost
- `Blizzard` - damage scales with Frost channeled
- Frost provides defense through block

### Dark Synergies
- `Darkness`, `Doom and Gloom`, `Rainbow` - Channel Dark
- Dark orb stores damage over time
- Best evoked when fully charged

### 0-Cost Synergies
- `All For One` - return all 0-cost cards from discard
- `Scrape` (if existed), various 0-cost cards: Beam Cell, Go for the Eyes, Steam Barrier, FTL, Turbo, Chill, Impulse, Overclock

### Power Synergies
- `Heatsinks` - draw on Power play
- `Storm` - Channel Lightning on Power play
- `Force Field` - cost reduced per Power played
- `Amplify` - double next Power
- `Creative AI` - generate Powers

---

## Notable Mechanics

### Orb Slot Management
- Start with 3 orb slots
- `Capacitor` - +2 slots
- `Consume` - -1 slot for +2 Focus
- `Inserter` (relic) - +1 slot every 2 turns

### X-Cost Cards
| Card | Effect |
|------|--------|
| Multi-Cast | Evoke X times |
| Reinforced Body | Gain 7 block X times |
| Tempest | Channel X Lightning |
| Doom and Gloom, Sunder, Meteor Strike | High fixed costs |

### Scaling Cards
- `Blizzard` - damage per Frost channeled ever
- `Thunder Strike` - hits per Lightning channeled ever
- `Genetic Algorithm` - permanently gains block
- `Stack` - block equals discard pile size
- `Barrage` - damage per orb channeled
- `Compile Driver` - draw per unique orb type

### Status Generation
- `Turbo` - adds Void (unplayable, lose 1 energy when drawn)
- `Overclock` - adds Burn (take damage when drawn)
