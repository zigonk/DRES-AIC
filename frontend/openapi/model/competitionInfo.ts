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
import { Task } from './task';
import { Team } from './team';


export interface CompetitionInfo { 
    id: number;
    name: string;
    status: CompetitionInfo.StatusEnum;
    description: string;
    currentTask?: Task;
    teams: Array<Team>;
}
export namespace CompetitionInfo {
    export type StatusEnum = 'CREATED' | 'ACTIVE' | 'PREPARING_TASK' | 'RUNNING_TASK' | 'TERMINATED';
    export const StatusEnum = {
        CREATED: 'CREATED' as StatusEnum,
        ACTIVE: 'ACTIVE' as StatusEnum,
        PREPARINGTASK: 'PREPARING_TASK' as StatusEnum,
        RUNNINGTASK: 'RUNNING_TASK' as StatusEnum,
        TERMINATED: 'TERMINATED' as StatusEnum
    };
}


