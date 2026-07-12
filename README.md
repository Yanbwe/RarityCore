# RarityCore

[![Modrinth](https://img.shields.io/badge/Modrinth-RarityCore-1bd1a5?logo=modrinth)](https://modrinth.com/mod/raritycore)
[![CurseForge](https://img.shields.io/badge/CurseForge-RarityCore-1bd1a5?logo=curseforge)](https://www.curseforge.com/minecraft/mc-mods/raritycore)
[![Wiki](https://img.shields.io/badge/Wiki-RarityCore-1bd1a5?logo=wiki)](https://yanbwe.github.io/Yanbwe-Wiki/raritycore/)
[![Forge](https://img.shields.io/badge/Forge-1.20.1-orange?logo=forge)](https://files.minecraftforge.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1|1.21.11|26.1.x-orange?logo=forge)](https://files.minecraftneoforge.net/)
[![License](https://img.shields.io/badge/License-GPL--3.0-green)](LICENSE)
[![MC百科](https://img.shields.io/badge/MC百科-稀有度核心-8B4513)](https://www.mcmod.cn/class/24499.html)

Revolutionize Minecraft's rarity system — more tiers, automated assignment, and powerful customization.

**Give every item the glory it deserves.**  

---

## 📖 Description

**RarityCore** is a mod designed to make "rarity" truly perceptible by players and fully customizable. It moves far beyond vanilla's limited color palette, offering:

- **7 preset rarity tiers**, each with unique background and tooltip styles.
- **Automatic rarity assignment for items from over 70 popular mods** (ready to use out of the box).
- **Smart rarity calculation based on crafting recipes**, covering more than 70% of all items.
- **Multiple configuration methods**: config files, in‑game commands, NBT matching, and KubeJS API.
- **Visual editing**: modify item rarity directly by clicking in the Creative inventory.

Whether you're a casual player or a modpack author, RarityCore makes your game world more intuitive and beautifully polished.

## ✨ Key Features

| Feature | Description |
| :--- | :--- |
| 🎨 **Rich Visuals** | Each rarity tier has its own **colored gradient background**, text color, and tooltip style. |
| 🔧 **Smart Automation** | - Automatically recognizes items from 70+ major mods<br>- Calculates rarity based on recipe materials/difficulty |
| 🖱️ **Visual Configuration** | Open your inventory in Creative mode → hold a key (e.g., `Ctrl`) and click an item → change its rarity instantly. |
| ⚙️ **Multi-Dimensional Matching** | - Match by item ID<br>- Match by exact NBT data (e.g., sub‑items from TacZ)<br>- Regex support |
| 🧩 **KubeJS Integration** | Provides `RarityCoreAPI` for KubeJS scripts to dynamically register/modify rarities. |
| 🔗 **Ecosystem Integration** | - Cross‑compatibility with rarity systems from **Apotheosis**, **Iron's Spells 'n Spellbooks**, and others<br>- Actively supports GUIs like **Refined Storage** to ensure correct background rendering |

## 🔌 Developer API

RarityCore provides a simple Java API for other mods to call directly.  
https://yanbwe.github.io/Yanbwe-Wiki/en/raritycore/

### Add as a Dependency

**Recommended: Modrinth Maven (no authentication required)**

<details>
<summary>1.20.1 (Forge) — click to expand</summary>

```gradle
repositories {
    mavenCentral()
    maven {
        name = "Modrinth"
        url = "https://api.modrinth.com/maven"
        content {
            includeGroup "maven.modrinth"
        }
    }
}

dependencies {
    implementation fg.deobf("maven.modrinth:raritycore:1201.14.0")
}
```
</details>

<details>
<summary>1.21.1 (NeoForge) — click to expand</summary>

```gradle
repositories {
    mavenCentral()
    maven {
        name = "Modrinth"
        url = "https://api.modrinth.com/maven"
        content {
            includeGroup "maven.modrinth"
        }
    }
}

dependencies {
    implementation "maven.modrinth:raritycore:<version>"
}
```
</details>

> The version can be found on the [Modrinth version page](https://modrinth.com/mod/raritycore/versions).

**Alternative: GitHub Packages (requires authentication)**

<details>
<summary>1.20.1 (Forge) — click to expand</summary>

```gradle
repositories {
    mavenCentral()
    maven {
        url = "https://maven.pkg.github.com/Yanbwe/RarityCore"
        credentials {
            username = "your-github-username"
            password = "your-github-personal-access-token"
        }
    }
}

dependencies {
    implementation fg.deobf("org.yanbwe:raritycore:1201.14.0")
}
```
</details>

<details>
<summary>1.21.1 (NeoForge) — click to expand</summary>

```gradle
repositories {
    mavenCentral()
    maven {
        url = "https://maven.pkg.github.com/Yanbwe/RarityCore"
        credentials {
            username = "your-github-username"
            password = "your-github-personal-access-token"
        }
    }
}

dependencies {
    implementation "org.yanbwe:raritycore:1211.<version>"
}
```
</details>

> Create a Personal Access Token (with `read:packages` scope) at:  
> `GitHub Settings → Developer settings → Personal access tokens → Fine-grained tokens`  
> You can store credentials in `~/.gradle/gradle.properties` to avoid hardcoding:
> ```properties
> gpr.user=your-github-username
> gpr.key=your-personal-access-token
> ```

<details>
<summary>1.20.1 (Forge) — click to expand</summary>

```gradle
repositories {
    mavenCentral()
    maven {
        name = "Modrinth"
        url = "https://api.modrinth.com/maven"
        content {
            includeGroup "maven.modrinth"
        }
    }
}

dependencies {
    implementation fg.deobf("maven.modrinth:raritycore:1201.14.0")
}
```
</details>

<details>
<summary>1.21.1 (NeoForge) — click to expand</summary>

```gradle
repositories {
    mavenCentral()
    maven {
        name = "Modrinth"
        url = "https://api.modrinth.com/maven"
        content {
            includeGroup "maven.modrinth"
        }
    }
}

dependencies {
    implementation "maven.modrinth:raritycore:<version>"
}
```
</details>

> The version can be found on the [Modrinth version page](https://modrinth.com/mod/raritycore/versions).

## 📜 License

This project is licensed under **GPL-3.0**.  
You are free to use, modify, and distribute it, but any derivative work must also be open source and licensed under the same terms.

✨ Happy crafting!
