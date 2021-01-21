/**
 * DRES API
 * API for DRES (Distributed Retrieval Evaluation Server), Version 1.0
 *
 * The version of the OpenAPI document: 1.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */
import { ScoreSeries } from './scoreSeries';


export interface RunScoreSeries { 
    teams: Array<string>;
    names: Array<string>;
    series: Array<ScoreSeries>;
}

