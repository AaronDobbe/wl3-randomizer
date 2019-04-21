# wl3-randomizer
Wario Land 3 Randomizer

This is a randomizer for Wario Land 3! It shuffles the contents of all 100 treasure chests, allowing for a new playthrough every time. If you want, you can also randomize the world map and game music! No matter what, everything will be shuffled in a way that allows you to complete the game. 

## Downloads
Download the randomizer at: https://github.com/AaronDobbe/wl3-randomizer/releases

## How to use
1. Extract the `.rar` archive and run the executable `wl3-randomizer.jar`.
2. Click the "Open Vanilla ROM..." button and select a clean copy of Wario Land 3.
3. Set your desired options. (See "Game options" below for a description of what these do.)
4. Click the "Generate Randomized Game" button.
5. A `.gbc` file will be created in the same directory as `wl3-randomizer.jar`. Open this file in a GBC emulator to play. ([bgb](http://bgb.bircd.org/) is highly recommended!)

## I'm stuck!
Some treasures can be acquired in unintuitive ways. Check out the ["Gotchas" doc](https://docs.google.com/document/d/1fYzp5uflcDFz836a_4Erpzw49fJjjvobjjyRKRJPWCQ/edit?usp=sharing) for a list of places where players often get stuck. However, if you really think you've found an impossible seed, be sure to report it!

## Racing
If you plan to race the game with one or more opponents, to keep things fair, you should use the "Seed" function to ensure all players have the same randomized game. To do this:
1. One player should generate a randomized game as above, with agreed-upon game options.
2. In the log below the "Generate Randomized Game" button, an 11-character seed will appear. This player should copy the seed and provide it to all other players.
3. All other players should enter this seed in the "Seed" text field, set their game options to the agreed-upon settings, then generate their own randomized games using the above steps. The generated ROM will be identical to everyone else's, allowing for a fair race.

## Differences from original game
In addition to shuffling treasures, the randomizer makes a few changes to the game rules to allow treasures to be collected in any order:
* In the original game, collecting a powerup item would grant Wario the abilities of all powerups that came before it in the intended sequence, and remove the abilities of all powerups that came after it. This has been fixed so that collecting a powerup ONLY grants you the ability of that powerup.
* In the original game, collecting certain level-transforming items would cause other level transformations to take effect even though Wario doesn't have the items that are intended to cause them. This has been fixed so that collecting any stage-transforming item will ONLY cause the appropriate transformation.
* In the original game, Wario only needed the fifth and final music box to enter the final battle. This has been fixed so that Wario requires all 5 music boxes to enter the final battle.

## Game options
* **Seed:** Allows you to generate a randomized game identical to another player's. See "Racing" above.
* **Shuffle world map:** Shuffles the location of every level on the world map. Recommended for experienced players.
* **Bosses guard music boxes:** If checked, music boxes will only appear in treasure chests guarded by a boss. Recommended for competitive play.
* **Guarantee axe start:** If checked, the axe will appear in the gray chest of the first level. Recommended for beginners, as it guarantees access to The Temple for the whole game.
* **Remove junk items:** If checked, all useless items will be removed from the game, including the chests that would have contained them. Be sure to pause the game inside a level to check which boxes have treasure! Recommended for competitive play, but speeds up casual playthroughs as well.
* **Temple hints:** Affects the behavior of hints from The Temple.
  * **Vanilla:** Reveals the locations of items in the same order as the vanilla game, regardless of whether you can get them or not. Generally unhelpful.
  * **Corrected:** Reveals the location of an item you can get to with your current inventory. Helpful if you get stuck! Recommended for beginners.
  * **Strategic:** Hints at the location of the axe, the five music boxes (in the order you're expected to find them), and then the powerups needed to defeat Rudy. Recommended for experienced players as a longer alternative to "Bosses guard music boxes".
* **Music:** Affects the game music.
  * **Vanilla:** Music will not be affected.
  * **Shuffled:** The soundtrack will be shuffled, so that every track appears exactly as many times as it appeared in the original game.
  * **Chaos:** Each instance of (looping) music in the whole game will be replaced by a randomly-selected (looping) music track, with no guarantees of balance. This can either be really annoying or really incredible.  

## Known issues
* The tutorial after collecting a powerup will often show powerups Wario doesn't have along the bottom of the screen, and also fail to show powerups that wario *does* have. This is purely cosmetic.
* The "CLEAR" screen will often incorrectly show which music boxes Wario has collected. Again, this is purely cosmetic. You can easily check which music boxes Wario actually has by pressing SELECT in the world map and selecting the treasure icon.
* Sometimes, saving and reloading mid-level will cause enemies to change color. This is also purely cosmetic.

Many emulators run WL3 somewhat poorly, causing odd graphical/sound errors or randomly causing Wario to be unable to climb ladders through screen transitions. Using [bgb](http://bgb.bircd.org/) is recommended if you're experiencing these issues.

## Tips
While the randomizer will never require you to use any glitches or difficult tricks (such as throwing an enemy and bouncing off it in midair), there are a few unusual mechanics you will need to know.
* If you collect an item that connects two quadrants of the world map, you can warp between them by pressing SELECT and selecting the NEXT MAP buttons. This may be required to get to levels you otherwise couldn't reach.
* Just because a level's "dot" isn't available on the world map doesn't mean you can't enter that level. For example, collecting the torch early allows you to walk to E4 The Colossal Hole and enter the level even though the hole itself hasn't appeared on the map.
* Small barrels can be nudged around by slowly walking into them while crouching, allowing you to reposition them and use them as platforms even if you don't have any gloves.
* If you're new to the game or even just new to the randomizer, try setting hints to "Corrected" and checking "Guarantee axe start" for your first few games. That way, if you get stuck, you can visit the Temple for direction on where to go.
* The ["Gotchas" doc](https://docs.google.com/document/d/1fYzp5uflcDFz836a_4Erpzw49fJjjvobjjyRKRJPWCQ/edit?usp=sharing) has a list of places that players commonly get stuck - if you're having trouble proceeding, consider giving it a read!

## Discord
The Wario Land 3 Randomizer has its own channel on the [Wario Series Speedruns Discord](https://discord.gg/gfrMAVv)! Join the discussion, find opponents to race, or just vent about being forced to visit Above the Clouds without any powerups. 
