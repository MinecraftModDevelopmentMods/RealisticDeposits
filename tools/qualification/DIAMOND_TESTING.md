# Diamond visual checks

These steps use a separate test profile. They do not prepare a release or open
your usual development saves.

1. Run `prepareDiamondDepositsManual` in Gradle, then refresh the project in
   Eclipse.
2. Start `runDiamondDepositsClient.launch`. Its saves and configuration live
   under `run-diamond-deposits`.
3. Create a Creative world with seed `3346071746553592696`, normal terrain and
   commands enabled. Keep OreSpawn's default geology and both diamond rules on.
4. Use `/realisticdeposits preview 32 45 realisticdeposits:overworld_diamond_pipes`
   to preview that region without changing blocks. If using Mineralogy, use a
   separate new world: its geology may select different sites.
   The standalone district is centred at X 16611, Z 23204. Start with
   `/tp 16611 90 23204` and use Spectator mode to inspect its depth.
5. Inspect the pipe in Spectator mode or expose a narrow vertical slice in the
   disposable world. Look for an irregular, widening upper body, a narrower
   root and scattered diamonds rather than a solid column. The surrounding
   stone stays unchanged; no kimberlite or lamproite blocks have been added.
6. Look in nearby rivers for subtly flecked gravel below the water. Rivers do
   not all contain placers, and patches need existing sand or gravel. Mining a
   source pipe should not affect placers in chunks explored later.
   A separate surveyed river patch is around X 131505, Z 132973, at Y 54-59:
   `/tp 131505 65 132973`. It uses the same seed and standalone settings.
7. Mine several rich blocks normally, then try Fortune and Silk Touch. Normal
   mining leaves ordinary gravel and usually no diamond. Silk Touch gives only
   the rich block. Dropping that block onto a torch should return its item,
   not extract diamonds. Place spent ordinary gravel again and confirm it
   behaves like vanilla gravel.
8. Place rich gravel high above a solid floor, let it fall, save and reload.
   It should remain rich gravel. Check its inventory icon and all block faces.
9. Try a resource pack which replaces vanilla gravel. The background should
   change while the subtle flecks remain. Ore and river terrain should not
   move when the world is reloaded.

Both diamond definitions are in `diamonds.json`. Restart after changing their
geometry or source link. To test disabling generation, create another new
world after disabling the pipe rule; its linked placer must also be off. A
world which already captured those rules keeps its saved OreSpawn settings.

## Surface access to a pipe

Use `prepareUndergroundMinesManual`, refresh Eclipse and start
`runUndergroundMinesClient.launch`. This uses fresh settings under
`run-underground-mines` rather than changing an older profile's opt-outs.
Create another normal Creative world with the seed above. Workings are enabled
for every iron formation, coalfield and diamond pipe where safe; rivers are
left alone.

The standalone pipe-access test is near `/tp 49521 75 56635`, with the entrance
at **49521, 67, 56635** and underground landing at **49497, 24, 56653**.
Walk down the stairs and turning landings, then explore the short crosscuts.
You should be able to walk back up without jumping or using a ladder. Save
and reload, then check the route again. If you change geology or terrain settings,
sites may differ. Some deposits have no usable entrance because water, trees,
structures or older chunks prevent safe excavation.

The regular `runClient` profile also has workings enabled in its local
catalogues. Restart Minecraft and create a **new** world to try them there.
This does not change the settings or mine layouts in any existing save.

Do not use original worlds for these checks. An older world must keep its
existing profile and background diamonds without receiving the new rules.
