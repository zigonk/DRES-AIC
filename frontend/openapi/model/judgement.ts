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


export interface Judgement { 
    token: string;
    verdict: Judgement.VerdictEnum;
}
export namespace Judgement {
    export type VerdictEnum = 'CORRECT' | 'WRONG' | 'INDETERMINATE' | 'UNDECIDABLE';
    export const VerdictEnum = {
        CORRECT: 'CORRECT' as VerdictEnum,
        WRONG: 'WRONG' as VerdictEnum,
        INDETERMINATE: 'INDETERMINATE' as VerdictEnum,
        UNDECIDABLE: 'UNDECIDABLE' as VerdictEnum
    };
}

