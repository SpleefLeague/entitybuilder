/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.spleefleague.entitybuilder;

import org.bson.Document;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author jonas
 */
public class PrimitiveSerialization {
    
    public static class Empty extends DBEntity implements DBSaveable, DBLoadable {
        
    } 
    
    public static class IntValues extends DBEntity implements DBSaveable, DBLoadable {
        
        @DBSave(fieldName = "primitive")
        @DBLoad(fieldName = "primitive")
        private int primitive;
        @DBSave(fieldName = "wrapper")
        @DBLoad(fieldName = "wrapper")
        private Integer wrapper;
    }
    
    @Test
    public void testEmpty() {
        Document doc = EntityBuilder.serialize(new Empty()).get("$set", Document.class);
        EntityBuilder.deserialize(doc, Empty.class);
    }
    
    @Test
    public void testInt() {
        IntValues iv = new IntValues();
        iv.primitive = 3;
        iv.wrapper = 4;
        Document doc = EntityBuilder.serialize(iv).get("$set", Document.class);
        IntValues r = EntityBuilder.deserialize(doc, IntValues.class);
        assertEquals(iv.primitive, r.primitive);
        assertEquals(iv.wrapper, r.wrapper);
    }
}
