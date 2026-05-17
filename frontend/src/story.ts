export interface KeywordItem {
  word: string;
  meaning: string;
}

export interface Sentence {
  index?: number;
  english: string;
  chinese: string;
  keywords?: KeywordItem[];
}

export interface BookPage {
  pageNo: number;
  imageUrl?: string;
  rawText?: string;
  needOcr?: boolean;
  parseError?: string;
  sentences: Sentence[];
}

export interface Book {
  id: string;
  title: string;
  englishTitle: string;
  level: string;
  sourceFileName?: string;
  sourceFileType?: string;
  coverUrl?: string;
  pages: BookPage[];
  createdAt?: string;
  updatedAt?: string;
}

export interface BookListItem {
  id: string;
  title: string;
  englishTitle: string;
  level: string;
  pageCount: number;
  createdAt?: string;
}

export interface StoryPage {
  page: number;
  sentences: Sentence[];
}

export interface StoryResponse {
  title: string;
  englishTitle: string;
  level: string;
  pages: StoryPage[];
}

export const fallbackBook: Book = {
  id: 'default_story',
  title: '小兔子',
  englishTitle: 'The Little Rabbit',
  level: '初学者',
  pages: [
    {
      pageNo: 1,
      rawText: 'The little rabbit is looking for his red hat. He asks the bird, have you seen my hat?',
      sentences: [
        {
          index: 0,
          english: 'The little rabbit is looking for his red hat.',
          chinese: '小兔子正在找他的红帽子。',
          keywords: [
            { word: 'rabbit', meaning: '小兔子' },
            { word: 'red hat', meaning: '红帽子' },
          ],
        },
        {
          index: 1,
          english: 'He asks the bird, have you seen my hat?',
          chinese: '他问鸟儿：你见过我的帽子吗？',
          keywords: [
            { word: 'bird', meaning: '鸟儿' },
            { word: 'hat', meaning: '帽子' },
          ],
        },
      ],
    },
    {
      pageNo: 2,
      rawText: 'The bird says, look under the tree. The rabbit finds his red hat.',
      sentences: [
        {
          index: 0,
          english: 'The bird says, look under the tree.',
          chinese: '鸟儿说：看看树下面。',
          keywords: [{ word: 'under the tree', meaning: '在树下面' }],
        },
        {
          index: 1,
          english: 'The rabbit finds his red hat.',
          chinese: '小兔子找到了他的红帽子。',
          keywords: [{ word: 'finds', meaning: '找到了' }],
        },
      ],
    },
  ],
};
