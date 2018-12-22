package com.lyeeedar.Game

import com.badlogic.gdx.utils.Array
import com.lyeeedar.Game.Level.TowerUpgradeTree

class GameData
{
	val unlockedTowers = Array<TowerUpgradeTree>()

	init
	{
		unlockedTowers.add(TowerUpgradeTree.load("Arrow"))
		unlockedTowers.add(TowerUpgradeTree.load("CrystalShot"))
	}
}