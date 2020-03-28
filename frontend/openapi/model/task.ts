/**
 * 
 * DRES API
 *
 * The version of the OpenAPI document: 1.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


export interface Task { 
    id: number;
    name: string;
    type: Task.TypeEnum;
    novice: boolean;
    description: object;
}
export namespace Task {
    export type TypeEnum = 'KIS_VISUAL' | 'KIS_TEXTUAL' | 'AVS';
    export const TypeEnum = {
        KISVISUAL: 'KIS_VISUAL' as TypeEnum,
        KISTEXTUAL: 'KIS_TEXTUAL' as TypeEnum,
        AVS: 'AVS' as TypeEnum
    };
}

