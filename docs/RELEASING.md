# Releasing Realistic Deposits

This candidate uses published OreSpawn 4.1.5.110021, pinned by file and checksum.
The publication guard still rejects local OreSpawn candidates; a release must
use the public dependency, not an experimental jar.

The **Release Realistic Deposits** action runs from the repository's default
branch. Enter the full numeric version, such as `0.5.0.110021`; the final
component identifies the Minecraft version and loader. CurseForge's alpha,
beta or release label is separate from the numeric version.

First run with **mode: validate**. This checks the branch, version, Java
settings and successful CI, then builds and checksums a release bundle. It
does not create a tag or upload anything.

To publish, select **mode: publish**, choose the intended CurseForge channel
(**alpha** for 0.5.0.110021), and tick the explicit publication confirmation.
Supply the SHA-256 of the verified validation bundle's `SHA256SUMS` file as
`expected_checksums_sha256` to require an exact match before publication.
The workflow requires all four secrets:

- `CURSEFORGE_TOKEN`
- `MAVEN_UPLOAD_URL`
- `MAVEN_UPLOAD_USERNAME`
- `MAVEN_UPLOAD_PASSWORD`

The upload order is Maven, CurseForge, then GitHub. Each job downloads the
same immutable bundle and checks its hashes. OreSpawn is a required
CurseForge dependency. The current project ID is `1689703`.

The channel does not change the numeric version or GitHub prerelease status.
Validation mode is the default; changing the channel does not authorize an upload.

Do not upload a second file because the first is awaiting moderation. If a
job fails after an upload, check its receipt and public destination before
retrying. Published versions and tags must never be replaced with new bytes.

Hosted CI/CD must stay lightweight: builds, unit/helper tests, static checks,
dependency/artifact guards and reproducibility. It must not launch Minecraft
servers, gameplay tests, surveys or policy matrices.

Before merging a release, check the README and changelog, repository hygiene
and unit tests. Run packaged policy/import tests, reloads and later-generated
chunks locally, retaining results and exact mod/dependency hashes. Reuse that
runtime evidence for workflow/docs-only changes only when all three release
jars and the public dependency are byte-identical. Back up important worlds
and keep the visual gameplay check separate from automated test results.
