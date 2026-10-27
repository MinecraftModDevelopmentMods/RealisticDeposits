# Ore Sources visual check

This tests the Ore Sources editor with Realistic Deposits 1.10.2.
It does not change your normal client, config files or saved worlds. Each
launch gets a new disposable folder under `run-editor-visual`.

In Eclipse, open **Run > External Tools > External Tools Configurations**
and select **Ore Sources Visual Test**. If it is not listed, refresh the
project and open the local `Ore Sources Visual Test.launch` file once.
The prepared candidate's checksums are checked before launch. Prepare the
ignored candidate manifest for the release being tested; older manifests do
not automatically follow a dependency update.

## What to try

The test menu has four profiles: an OS1 import, an OS3 import, an explicit
custom channel, and a fresh standard-channel control. These are small editor
snapshots with deliberately different iron settings and a disabled gold rule,
not worlds you need to explore.

For each profile:

1. Open **Rocks & Ores...**, choose **Ores**, then **Ore Sources...**.
2. Choose **Show All** if Iron is hidden, then select **Iron** and open its cog.
   Under **Placement Rules**, check there
   is one row, with a readable source name and no false "missing" warning.
   The imports and fresh control use ordinary veins; the custom profile must
   still show its own channel.
3. Click the placement row to switch between **Minecraft** and **OreSpawn**.
   Both sources output iron. Return with **Done** until you reach the main
   OreSpawn settings, then choose **Cancel**.
   Reopen the same profile: your choice should not have been saved.
4. Repeat the edit, but use **Done** on the main settings screen. Reopen the
   profile and check your new choice remains selected.
5. Switch **Manage Vanilla Ores** off and back on. Check the imported source
   is not reported as a missing mod and the chosen source is retained. Cancel
   if you do not want to keep those test changes.
6. Try a smaller window and hover the rows and help buttons. Look for clipped
   text, unreadable tooltips, duplicate rows or buttons that do not respond.

The fixture menu says whether a profile was saved or cancelled. Accepted
profiles are written to `editor-results` inside that launch's test folder;
they remain available when you reopen a profile during the same session.
Close the game with **Quit test**. Starting the launch again gives you clean
fixtures, without deleting the earlier results.

## Automated checks

`launch-editor.ps1 -Automated` opens a separate disposable client for each
profile. It checks the actual placement-screen rows, edits a source, exercises
Cancel and Done, saves screenshots, and reloads the accepted JSON. A completed
batch writes `PASS.txt`, with individual results under each profile's folder.
Failures retain their logs, screenshots and input profiles.

Screenshots are for troubleshooting, not visual approval. The automated
checks verify the controls and saved values; use the walkthrough to judge
the actual window, including text, tooltips and scrolling.

The log for each launch is `client.log` in its disposable folder.

The unit fixtures live in OreSpawn's `src/test/resources/ore-source-editor`.
Its optional test mod is built with `prepareOreSourceEditorFixture` and
`--init-script tools/qualification/editor/probe.init.gradle`. It is excluded
from normal builds and release jars. The prepared local artifact manifest
is ignored; this is not a change to Realistic Deposits' public dependency pin.
