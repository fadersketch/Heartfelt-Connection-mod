package com.heartfelt.connection;

import com.heartfelt.connection.client.ClientInit;
import com.heartfelt.connection.config.HeartfeltConfig;
import com.heartfelt.connection.item.HeartfeltItems;
import com.heartfelt.connection.network.HeartfeltNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Heartfelt-connection(心契誓约 × 爱憎分明)的 @Mod 入口。
 *
 * 定位:maidmarriage(心契誓约)与 Love Loathe(爱憎分明)之间的关系联动补丁——
 * 前置 maidmarriage + callresponse(Love Loathe)+ touhou_little_maid(API 基础)。
 *
 * v1.1.0:注册公共配置(HeartfeltConfig)。
 * v1.2.0:注册网络通道(女仆主动告白对话)+ 客户端初始化(DistExecutor)。
 * v1.4.3:注册物品(调整器,测试工具)。
 */
@Mod("heartfelt_connection")
public class HeartfeltMod {
    public HeartfeltMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, HeartfeltConfig.SPEC);
        // v1.4.3:物品注册(调整器)挂到 ModEventBus
        HeartfeltItems.ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        // v1.2.0:网络通道(告白对话 S2C/C2S)必须在发包前注册
        HeartfeltNetwork.channel();
        // v1.2.0:客户端侧初始化(仅客户端)
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientInit::init);
    }
}

