package com.backendless.transaction;

import java.util.HashMap;
import java.util.Map;

public class OpResult
{
  private String tableName;
  private Map<String, Object> reference;
  private OperationType operationType;

  public OpResult( String tableName, Map<String, Object> reference, OperationType operationType )
  {
    this.tableName = tableName;
    this.reference = reference;
    this.operationType = operationType;
  }

  public Map<String, Object> getReference()
  {
    return reference;
  }

  public OperationType getOperationType()
  {
    return operationType;
  }

  public String getTableName()
  {
    return tableName;
  }

  public Map<String, Object> resolveTo( String propName )
  {
    Map<String, Object> referencePropName = new HashMap<>( reference );
    referencePropName.put( UnitOfWork.PROP_NAME, propName );
    return referencePropName;
  }

  public Map<String, Object> resolveTo( int opResultIndex )
  {
    Map<String, Object> referenceIndex = new HashMap<>( reference );
    referenceIndex.put( UnitOfWork.RESULT_INDEX, opResultIndex );
    return referenceIndex;
  }

  public Map<String, Object> resolveTo( int opResultIndex, String propName )
  {
    Map<String, Object> referenceIndexPropName = new HashMap<>( reference );
    referenceIndexPropName.put( UnitOfWork.RESULT_INDEX, opResultIndex );
    referenceIndexPropName.put( UnitOfWork.PROP_NAME, propName );
    return referenceIndexPropName;
  }

  public OpResultIndex resolveToIndex( int opResultIndex )
  {
    Map<String, Object> referenceIndex = new HashMap<>( reference );
    referenceIndex.put( UnitOfWork.RESULT_INDEX, opResultIndex );
    return new OpResultIndex( tableName, referenceIndex, operationType );
  }
}
