# F-Droid server config for GitHub Pages-hosted custom repo.
# https://f-droid.org/docs/Build_Metadata_Reference/

repo_url = "https://T-vK.github.io/OpenMultiTrack/fdroid/repo"
repo_name = "OpenMultiTrack"
repo_description = "OpenMultiTrack nightly debug builds (FOSS multitrack recorder)"

archive_older = 5
keystore = "keystore.p12"
repo_keyalias = "fdroidrepo"
smartcardoptions = []

# Apps are discovered from APKs placed in repo/ before `fdroid update`.
apps = {}
