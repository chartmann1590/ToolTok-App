const latestApkUrl =
  "https://github.com/chartmann1590/ToolTok-App/releases/latest/download/ToolTok-release.apk";
const latestReleaseApiUrl =
  "https://api.github.com/repos/chartmann1590/ToolTok-App/releases/latest";

for (const link of document.querySelectorAll("[data-latest-apk-url]")) {
  link.href = latestApkUrl;
}

const releaseStatus = document.querySelector("[data-release-status]");

async function hydrateLatestRelease() {
  if (!releaseStatus) {
    return;
  }

  try {
    const response = await fetch(latestReleaseApiUrl, {
      headers: {
        Accept: "application/vnd.github+json"
      }
    });

    if (!response.ok) {
      throw new Error(`GitHub latest release request failed with ${response.status}`);
    }

    const release = await response.json();
    const apkAsset = Array.isArray(release.assets)
      ? release.assets.find((asset) => asset.name === "ToolTok-release.apk")
      : null;

    if (apkAsset?.browser_download_url) {
      for (const link of document.querySelectorAll("[data-latest-apk-url]")) {
        link.href = apkAsset.browser_download_url;
      }
    }

    const publishedAt = release.published_at || release.created_at;
    const formattedDate = publishedAt
      ? new Intl.DateTimeFormat("en-US", {
          dateStyle: "long"
        }).format(new Date(publishedAt))
      : null;
    const versionLabel = release.name || release.tag_name || "latest build";

    releaseStatus.textContent = formattedDate
      ? `Latest build: ${versionLabel}, published ${formattedDate}.`
      : `Latest build: ${versionLabel}.`;
  } catch (error) {
    console.warn("Unable to load the latest GitHub release metadata.", error);
    releaseStatus.textContent =
      "Download points to the latest GitHub release even if release metadata cannot be loaded here.";
  }
}

void hydrateLatestRelease();
