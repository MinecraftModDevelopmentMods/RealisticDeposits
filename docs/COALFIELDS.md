# Coalfields

Coalfields contain several thin beds separated by rock, rather than a cloud of
coal blocks. The beds share their folds and fault offsets. They thin towards
the edges, and a local rock parting can split a thicker bed.

Coalfields require OreSpawn 4.1.4.110021 or a later compatible 1.10.2 release.
Its exact-host API keeps plain stone separate from granite, diorite and andesite.

## Where to look

Coalfields favour sedimentary and wetland basins. They can also occur less
often beneath stable continental regions, coastal shelves and mountain belts.
Today's surface biome is not treated as proof of an ancient swamp.

With physical Mineralogy strata active, beds fit suitable shale, siltstone,
sandstone and conglomerate. They stop where their rock setting is unsuitable.
Limestone, dolomite, evaporites, igneous rocks and metamorphic rocks are not
default hosts. A broad rock-family selector cannot bypass that restriction.

Without those physical strata, plain vanilla stone is an approximation of the
coal-bearing sequence. Granite, diorite and andesite are still excluded. The
mod does not add surrounding rocks or turn on terrain replacement.

Some ordinary coal remains outside the fields. It follows the existing
background generator, not the new coal-bed model.

## Defaults

The separate file is `config/realisticdeposits/deposits/coal.json`. It is created
only if absent. The [packaged catalogue](../src/main/resources/assets/realisticdeposits/defaults/coal.json)
shows every setting.

- Overworld, Y 8-128.
- 32-chunk regions, with a 50% candidate chance before geological selection.
- 384-768 blocks along strike and 128-256 blocks across.
- Two to four beds, 1-3 blocks thick, separated by 4-12 blocks of rock.
- Hybrid generation with 15% ordinary background coal.
- Abandoned workings attempted at every new coalfield where safe. Existing
  catalogues and saved-world settings keep their choices.

Candidate selection is not a promise that every region has a coalfield.
Geology, available height, caves and safe hosts can reduce or reject a deposit.
Basins receive the strongest preference; the other listed geomes accept one
quarter of their otherwise selected candidates.

Coalfields place vanilla coal ore. Mining, Silk Touch and Fortune keep their
normal behaviour. There are no new coal grades, fuel recipes or hazards.

## Existing worlds and other coal settings

Existing worlds retain their saved OreSpawn definitions. Adding the new
catalogue does not silently add coalfields or reduce their background coal.
Use a new test world to try the defaults.

Some older Mineralogy worlds have Cyano coal layers, where coal is part of
the rock sequence itself. Those layers remain independent. Realistic Deposits
reports their presence but does not disable them or rewrite the saved choice.
Background ore controls do not remove coal supplied as a rock layer. If you
deliberately enable both, consider their combined supply.

The old Cyano geology sampler exposes a single `orespawn:cyano` geome rather
than the newer basin map. It is not in the default coalfield list. If you want
coalfields in a new legacy-geology world, add that geome to your coal definition
explicitly; it receives the lower, non-basin selection rate. This does not turn
off the older coal rock layers. Existing saved profiles still take precedence.

Changing shape parameters can affect later new chunks. Changes to resources,
hosts, interaction policy or shape type need a restart and must respect the
world's captured settings. There is no retrogen.

## Old workings

The existing `abandoned_mine` section can enable short workings along one
principal coal bed. It does not connect every bed or excavate the whole field.
Entrances still need dry, covered, suitable terrain; an enabled attempt can be
rejected. See [abandoned mines](ABANDONED_MINES.md).
