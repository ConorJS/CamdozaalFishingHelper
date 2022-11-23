package camdozaalfishing;

import com.aeimo.camdozaalfishing.CamdozaalFishingPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CamdozaalFishingPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CamdozaalFishingPlugin.class);
		RuneLite.main(args);
	}
}