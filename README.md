# wl3-randomizer
Wario Land 3 Randomizer

This is a randomizer for Wario Land 3! It shuffles the contents of all 100 treasure chests, allowing for a new playthrough every time. The treasures are always shuffled in a way that allows you to complete the game.

## How to use
1. Run the executable `wl3-randomizer.jar`.
2. Click the "Open Vanilla ROM..." button and select a clean copy of Wario Land 3.
3. Click the "Generate Randomized Game" button.
4. A `.gbc` file will be created in the same directory as `wl3-randomizer.jar`. Open this file in a GBC emulator to play. ([bgb](http://bgb.bircd.org/) is highly recommended!)

## Racing
If you plan to race the game with one or more opponents, to keep things fair, you should use the "Seed" function to ensure all players have the same randomized game. To do this:
1. One player should generate a randomized game as above.
2. In the log below the "Generate Randomized Game" button, an 11-character seed will appear. This player should copy the seed and provide it to all other players.
3. All other players should enter this seed in the "Seed" text field, then generate their own randomized games using the above steps. The generated ROM will be identical to everyone else's, allowing for a fair race.

## Differences from original game
In addition to shuffling treasures, the randomizer makes a few changes to the game rules to allow treasures to be collected in any order:
* In the original game, collecting a powerup item would grant Wario the abilities of all powerups that came before it in the intended sequence, and remove the abilities of all powerups that came after it. This has been fixed so that collecting a powerup ONLY grants you the ability of that powerup.
* In the original game, collecting certain level-transforming items would cause other level transformations to take effect even though Wario doesn't have the items that are intended to cause them. This has been fixed so that collecting any stage-transforming item will ONLY cause the appropriate transformation.
* In the original game, Wario only needed the fifth and final music box to enter the final battle. This has been fixed so that Wario requires all 5 music boxes to enter the final battle.

## Known issues
* The tutorial after collecting a powerup will often show powerups Wario doesn't have along the bottom of the screen, and also fail to show powerups that wario *does* have. This is purely cosmetic.
* The "CLEAR" screen will often incorrectly show which music boxes Wario has collected. Again, this is purely cosmetic. You can easily check which music boxes Wario actually has by pressing SELECT in the world map and selecting the treasure icon.
* Sometimes, saving and reloading mid-level will cause enemies to change color. This is also purely cosmetic.

Many emulators run WL3 somewhat poorly, causing odd graphical/sound errors or randomly causing Wario to be unable to climb ladders through screen transitions. Using [bgb](http://bgb.bircd.org/) is recommended if you're experiencing these issues.

## Tips
While the randomizer will never require you to use any glitches or difficult tricks (such as throwing an enemy and bouncing off it in midair), there are a few unusual mechanics you will need to know.
* If you collect an item that connects two quadrants of the world map, you can warp between them by pressing SELECT and selecting the NEXT MAP buttons. This may be required to get to levels you otherwise couldn't reach.
* Just because a level's "dot" isn't available on the world map doesn't mean you can't enter that level. For example, collecting the torch early allows you to walk to E4 The Colossal Hole and enter the level even though the hole itself hasn't appeared on the map.
* Small barrels can be nudged around by slowly walking into them while crouching, allowing you to reposition them even if you don't have any gloves.

Finally, The Temple provides hints just like in the original game, though they may not be immediately helpful. The Temple will reveal the locations of items in the exact same order as in the original game (starting with the axe) even if you can't get to that item yet. This may change in the future.
