package com.drewdrew1.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class ItemStackCodec {
    private ItemStackCodec() {
    }

    public static String encode(ItemStack item) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(item);
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }
    }

    public static ItemStack decode(String data) throws IOException, ClassNotFoundException {
        byte[] raw = Base64.getDecoder().decode(data);
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(raw))) {
            Object object = input.readObject();
            if (object instanceof ItemStack item) {
                return item;
            }
            throw new IOException("Stored data is not an ItemStack.");
        }
    }
}
