"""Build and sign the Android APK with the JDK and Android SDK."""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
from zipfile import ZIP_DEFLATED, ZipFile


ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app" / "src" / "main"
BUILD = ROOT / "build"
PACKAGE_NAME = "Review-Streak.apk"


def run(*args, env=None):
    subprocess.run(list(map(str, args)), check=True, env=env)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, default=os.getenv("ANDROID_HOME"))
    parser.add_argument("--keystore", type=Path, help="Use an existing release signing key")
    parser.add_argument("--alias", default="review-streak")
    args = parser.parse_args()
    if not args.sdk:
        parser.error("Set ANDROID_HOME or pass --sdk.")

    sdk = args.sdk.resolve()
    tools = sdk / "build-tools" / "35.0.0"
    android = sdk / "platforms" / "android-35" / "android.jar"
    if not tools.is_dir() or not android.is_file():
        parser.error("Install Android SDK Platform 35 and Build Tools 35.0.0.")
    executable = ".exe" if os.name == "nt" else ""
    java_home = os.getenv("JAVA_HOME")
    java = Path(java_home) / "bin" if java_home else None

    def jdk(name):
        return (java / (name + executable)) if java else name

    def sdk_tool(name):
        return tools / (name + executable)

    BUILD.mkdir(exist_ok=True)
    work = BUILD / "intermediates"
    if work.exists():
        shutil.rmtree(work)
    classes = work / "classes"
    classes.mkdir(parents=True)
    dex = work / "dex"
    dex.mkdir()
    resources = work / "resources.zip"
    unsigned = work / "unsigned.apk"
    aligned = work / "aligned.apk"

    run(sdk_tool("aapt2"), "compile", "--dir", APP / "res", "-o", resources)
    run(sdk_tool("aapt2"), "link", "-I", android, "--manifest", APP / "AndroidManifest.xml",
        "-o", unsigned, resources)
    run(jdk("javac"), "-encoding", "UTF-8", "--release", "11", "-cp", android, "-d", classes,
        *sorted((APP / "java").rglob("*.java")))
    run(jdk("java"), "-cp", tools / "lib" / "d8.jar", "com.android.tools.r8.D8",
        "--lib", android, "--min-api", "26", "--output", dex, *sorted(classes.rglob("*.class")))
    with ZipFile(unsigned, "a", compression=ZIP_DEFLATED) as apk:
        apk.write(dex / "classes.dex", "classes.dex")
    run(sdk_tool("zipalign"), "-f", "4", unsigned, aligned)

    env = os.environ.copy()
    if args.keystore:
        keystore = args.keystore.resolve()
        if not keystore.is_file() or not env.get("REVIEW_STREAK_KEY_PASSWORD"):
            parser.error("Provide an existing --keystore and set REVIEW_STREAK_KEY_PASSWORD.")
    else:
        keystore = BUILD / "development.p12"
        env["REVIEW_STREAK_KEY_PASSWORD"] = "android"
        if not keystore.exists():
            run(jdk("keytool"), "-genkeypair", "-keystore", keystore, "-storetype", "PKCS12",
                "-alias", args.alias, "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
                "-dname", "CN=Review Streak Development", "-storepass:env", "REVIEW_STREAK_KEY_PASSWORD",
                env=env)

    signer = [jdk("java"), "-jar", tools / "lib" / "apksigner.jar"]
    output = BUILD / PACKAGE_NAME
    run(*signer, "sign", "--ks", keystore, "--ks-key-alias", args.alias,
        "--ks-pass", "env:REVIEW_STREAK_KEY_PASSWORD", "--v4-signing-enabled", "false",
        "--out", output, aligned, env=env)
    run(*signer, "verify", "--verbose", output)
    print(output)


if __name__ == "__main__":
    main()
