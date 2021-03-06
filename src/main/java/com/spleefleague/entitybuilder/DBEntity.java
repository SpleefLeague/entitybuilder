/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.spleefleague.entitybuilder;

import org.bson.types.ObjectId;

/**
 *
 * @author Jonas
 */
public abstract class DBEntity {

    @DBLoad(fieldName = "_id", priority = Integer.MAX_VALUE)
    private ObjectId _id;

    public ObjectId getObjectId() {
        return _id;
    }
    
    public void setObjectId(ObjectId _id) {
        this._id = _id;
    }
}
