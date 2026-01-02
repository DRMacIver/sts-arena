# Watcher (Purple) Cards Reference

This document provides reference information for all Watcher cards, with special attention to card IDs that differ from display names.

## IMPORTANT: Card ID Naming

**Watcher cards use CamelCase IDs**, unlike other classes which use spaces. This is a common source of bugs when looking up cards.

### Cards Where ID Differs From Display Name

| Display Name | Card ID | Notes |
|--------------|---------|-------|
| Tranquility | `ClearTheMind` | Skill - enters Calm stance |
| Foresight | `Wireheading` | Power - scry at turn start |
| Rushdown | `Adaptation` | Power - draw on Wrath entry |
| Pressure Points | `PathToVictory` | Skill - mark synergy |
| Simmering Fury | `Vengeance` | Skill - delayed Wrath + draw |
| Fasting | `Fasting2` | Power - +Str/Dex, -1 energy |

---

## Basic Cards

| Card ID | Display Name | Type | Cost | Effect |
|---------|--------------|------|------|--------|
| `Strike_P` | Strike | ATTACK | 1 | Deal 6 damage |
| `Defend_P` | Defend | SKILL | 1 | Gain 5 block |
| `Eruption` | Eruption | ATTACK | 2 | Deal 9 damage, enter Wrath |
| `Vigilance` | Vigilance | SKILL | 2 | Gain 8 block, enter Calm |

---

## Common Cards

### Attacks
| Card ID | Display Name | Cost | Effect |
|---------|--------------|------|--------|
| `BowlingBash` | Bowling Bash | 1 | Deal 7 damage (+per enemy) |
| `Consecrate` | Consecrate | 0 | Deal 5 damage to ALL |
| `CrushJoints` | Crush Joints | 1 | Deal 8 damage, apply Vulnerable if skill played |
| `CutThroughFate` | Cut Through Fate | 1 | Deal 7 damage, Scry 2, draw 1 |
| `EmptyFist` | Empty Fist | 1 | Deal 9 damage, exit stance (EMPTY tag) |
| `FlurryOfBlows` | Flurry of Blows | 0 | Deal 4 damage, return on stance change |
| `FlyingSleeves` | Flying Sleeves | 1 | Deal 4x2 damage, Retain |
| `FollowUp` | Follow Up | 1 | Deal 7 damage (+bonus if attack played) |
| `JustLucky` | Just Lucky | 0 | Scry 1, gain 2 block, deal 3 damage |
| `SashWhip` | Sash Whip | 1 | Deal 8 damage, apply Weak if attack played |

### Skills
| Card ID | Display Name | Cost | Effect |
|---------|--------------|------|--------|
| `ClearTheMind` | Tranquility | 1 | Enter Calm, Exhaust, Retain |
| `Crescendo` | Crescendo | 1 | Enter Wrath, Exhaust, Retain |
| `EmptyBody` | Empty Body | 1 | Gain 7 block, exit stance (EMPTY tag) |
| `Evaluate` | Evaluate | 1 | Gain 6 block, add Insight to draw pile |
| `Halt` | Halt | 0 | Gain 3+ block (scales in Wrath) |
| `PathToVictory` | Pressure Points | 1 | Apply 8 Mark, trigger all Marks |
| `Prostrate` | Prostrate | 0 | Gain 4 block, gain 2 Mantra |
| `Protect` | Protect | 2 | Gain 12 block, Retain |
| `ThirdEye` | Third Eye | 1 | Gain 7 block, Scry 3 |

---

## Uncommon Cards

### Attacks
| Card ID | Display Name | Cost | Effect |
|---------|--------------|------|--------|
| `CarveReality` | Carve Reality | 1 | Deal 6 damage, add Smite to hand |
| `Conclude` | Conclude | 1 | Deal 12 damage to ALL, end turn |
| `FearNoEvil` | Fear No Evil | 1 | Deal 8 damage (double if attacking) |
| `ReachHeaven` | Reach Heaven | 2 | Deal 10 damage, add Through Violence to draw pile |
| `SignatureMove` | Signature Move | 2 | Deal 30 damage (only if no other attacks in hand) |
| `Tantrum` | Tantrum | 1 | Deal 3x3 damage, enter Wrath, shuffle into deck |
| `TalkToTheHand` | Talk to the Hand | 1 | Deal 5 damage, apply Block Return, Exhaust |
| `Wallop` | Wallop | 2 | Deal 9 damage, gain block equal to damage dealt |
| `Weave` | Weave | 0 | Deal 4 damage, return on Scry |
| `WheelKick` | Wheel Kick | 2 | Deal 15 damage, draw 2 |
| `WindmillStrike` | Windmill Strike | 2 | Deal 7 damage, Retain, +4 damage per retain (STRIKE tag) |
| `SandsOfTime` | Sands of Time | 4 | Deal 20 damage, Retain, -1 cost per retain |

### Skills
| Card ID | Display Name | Cost | Effect |
|---------|--------------|------|--------|
| `Collect` | Collect | X | Add X+1 Miracles to hand, Exhaust |
| `DeceiveReality` | Deceive Reality | 1 | Gain 4 block, add Safety to hand |
| `EmptyMind` | Empty Mind | 1 | Draw 2, exit stance (EMPTY tag) |
| `Fasting2` | Fasting | 2 | Gain 3 Str and Dex, lose 1 energy next turn |
| `ForeignInfluence` | Foreign Influence | 0 | Choose 1 of 3 attack cards from other classes, Exhaust |
| `Indignation` | Indignation | 1 | If in Wrath: apply 5 Vulnerable to ALL; else: enter Wrath |
| `InnerPeace` | Inner Peace | 1 | If in Calm: draw 3; else: enter Calm |
| `Meditate` | Meditate | 1 | Put 1 card from discard in hand, Retain it, enter Calm, end turn |
| `Perseverance` | Perseverance | 1 | Gain 5 block, Retain, +2 block per retain |
| `Pray` | Pray | 1 | Gain 3 Mantra, add Insight to draw pile |
| `Sanctity` | Sanctity | 1 | Gain 6 block (+2 draw if skill played) |
| `Swivel` | Swivel | 2 | Gain 8 block, next attack costs 0 |
| `Vengeance` | Simmering Fury | 1 | Next turn: enter Wrath, draw 2 |
| `WaveOfTheHand` | Wave of the Hand | 1 | This turn: block gained applies Weak |
| `Wireheading` | Foresight | 1 | At turn start: Scry 3 |
| `Worship` | Worship | 2 | Gain 5 Mantra (Retain when upgraded) |
| `WreathOfFlame` | Wreath of Flame | 1 | Next attack deals +5 damage |

### Powers
| Card ID | Display Name | Cost | Effect |
|---------|--------------|------|--------|
| `Adaptation` | Rushdown | 1 | Draw 2 when entering Wrath |
| `BattleHymn` | Battle Hymn | 1 | At turn start: add Smite to hand (Innate+) |
| `Devotion` | Devotion | 1 | At turn start: gain 2 Mantra |
| `LikeWater` | Like Water | 1 | If in Calm at end of turn: gain 5 block |
| `MentalFortress` | Mental Fortress | 1 | When changing stance: gain 4 block |
| `Nirvana` | Nirvana | 1 | When Scrying: gain 3 block |
| `Study` | Study | 2 | At end of turn: add Insight to draw pile |

---

## Rare Cards

### Attacks
| Card ID | Display Name | Cost | Effect |
|---------|--------------|------|--------|
| `Brilliance` | Brilliance | 1 | Deal 12+ damage (scales with Mantra gained) |
| `LessonLearned` | Lesson Learned | 2 | Deal 10 damage, if fatal: upgrade random card, Exhaust |
| `Ragnarok` | Ragnarok | 3 | Deal 5x5 damage to random enemies |

### Skills
| Card ID | Display Name | Cost | Effect |
|---------|--------------|------|--------|
| `Alpha` | Alpha | 1 | Add Beta to draw pile, Exhaust (Innate+) |
| `Blasphemy` | Blasphemy | 1 | Enter Divinity, die next turn, Exhaust (Retain+) |
| `ConjureBlade` | Conjure Blade | X | Add Expunger(X) to hand, Exhaust |
| `DeusExMachina` | Deus Ex Machina | 0 | Unplayable. When drawn: add 2 Miracles, Exhaust |
| `Omniscience` | Omniscience | 4 | Choose a card, play it twice for free, Exhaust |
| `Scrawl` | Scrawl | 1 | Draw until 10 cards in hand, Exhaust |
| `SpiritShield` | Spirit Shield | 2 | Gain 3 block per card in hand |
| `Vault` | Vault | 3 | Take another turn (enemies skip their turn), Exhaust |
| `Wish` | Wish | 3 | Choose: Str/Dex, Gold, or MaxHP, Exhaust |

### Powers
| Card ID | Display Name | Cost | Effect |
|---------|--------------|------|--------|
| `DevaForm` | Deva Form | 3 | At turn start: gain 1 energy (stacks), Ethereal |
| `Establishment` | Establishment | 1 | Retained cards cost 1 less (Innate+) |
| `Judgement` | Judgement | 1 | If enemy has <= 30 HP: kill it instantly |
| `MasterReality` | Master Reality | 1 | Created cards are upgraded |

---

## Stance Mechanics

### Wrath Stance
- Deal and take **double damage**
- Enter with: Eruption, Tantrum, Crescendo, Indignation (if not in Wrath)

### Calm Stance
- When exiting: gain **2 energy**
- Enter with: Vigilance, Tranquility (ClearTheMind), Inner Peace

### Divinity Stance
- Gain **3 energy**, deal **triple damage**
- Exit at end of turn
- Enter with: Blasphemy, 10 Mantra

### Neutral Stance (Empty)
- No bonuses or penalties
- Cards tagged EMPTY exit to Neutral: EmptyFist, EmptyBody, EmptyMind

---

## Key Synergies

### Stance Change Synergies
- `FlurryOfBlows` - returns to hand on stance change
- `MentalFortress` - block on stance change
- `Adaptation` (Rushdown) - draw on Wrath entry

### Scry Synergies
- `Weave` - returns on Scry
- `Nirvana` - block on Scry
- `Wireheading` (Foresight) - Scry at turn start

### Retain Synergies
- `WindmillStrike` - gains damage
- `SandsOfTime` - reduces cost
- `Perseverance` - gains block
- `Establishment` - reduces cost

### Mantra Synergies
- `Brilliance` - scales with Mantra gained
- `Prostrate`, `Pray`, `Worship`, `Devotion` - generate Mantra
