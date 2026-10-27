# Releasing Realistic Deposits

The **Release Realistic Deposits** action runs from the repository's default
branch. Enter the full numeric version, such as `0.1.1.110021`; the final
component identifies the Minecraft version and loader. CurseForge's alpha,
beta or release label is separate from the numeric version.

First run with **mode: validate**. This checks the branch, version, Java
settings and successful CI, then builds and checksums a release bundle. It
does not create a tag or upload anything.

To publish, select **mode: publish**, choose **alpha**, and tick the explicit
publication confirmation. The workflow requires all four secrets:

- `CURSEFORGE_TOKEN`
- `MAVEN_UPLOAD_URL`
- `MAVEN_UPLOAD_USERNAME`
- `MAVEN_UPLOAD_PASSWORD`

The upload order is Maven, CurseForge, then GitHub. Each job downloads the
same immutable bundle and checks its hashes. OreSpawn is a required
CurseForge dependency. The current project ID is `1689703`.

Do not upload a second file because the first is awaiting moderation. If a
job fails after an upload, check its receipt and public destination before
retrying. Published versions and tags must never be replaced with new bytes.

Before merging a release, check the README and changelog, repository hygiene,
unit tests, packaged policy/import tests, reloads and later-generated chunks.
Back up important worlds and keep the visual gameplay check separate from
automated test results.
